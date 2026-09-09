package com.tripdiary.global.error;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void returnsSafeMessageForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("JSON 요청 형식을 확인해 주세요."));
    }

    @Test
    void returnsStandardResponseForUnknownResource() throws Exception {
        mockMvc.perform(get("/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsStandardResponseForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void returnsStandardResponseForInvalidParameterType() throws Exception {
        mockMvc.perform(get("/test/parameter").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다."));
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @PostMapping("/validation")
        public TestRequest validate(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping("/business")
        public void businessError() {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR);
        }

        @GetMapping("/unexpected")
        public void unexpectedError() {
            throw new IllegalStateException("sensitive internal detail");
        }

        @GetMapping("/parameter")
        public int parameter(@RequestParam int page) {
            return page;
        }
    }

    public record TestRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
            String title) {
    }
}
