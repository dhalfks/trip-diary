import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const { validatePublicApiUrl, validatePublicReleaseUrl } = require('../app.config.js');

test('development permits a local API URL', () => {
  assert.doesNotThrow(() =>
    validatePublicApiUrl('development', 'http://localhost:8080/api/v1'),
  );
});

test('production requires an API URL', () => {
  assert.throws(
    () => validatePublicApiUrl('production', undefined),
    /must be configured/,
  );
});

test('production rejects localhost and non-HTTPS API URLs', () => {
  assert.throws(
    () => validatePublicApiUrl('production', 'http://localhost:8080/api/v1'),
    /HTTPS.*localhost/,
  );
  assert.throws(
    () => validatePublicApiUrl('production', 'http://api.example.com/api/v1'),
    /HTTPS.*localhost/,
  );
  assert.throws(
    () => validatePublicApiUrl('production', 'https://0.0.0.0/api/v1'),
    /HTTPS.*localhost/,
  );
});

test('production requires public HTTPS privacy and terms URLs', () => {
  assert.throws(
    () => validatePublicReleaseUrl('production', 'EXPO_PUBLIC_PRIVACY_POLICY_URL', ''),
    /must be configured/,
  );
  assert.throws(
    () =>
      validatePublicReleaseUrl(
        'production',
        'EXPO_PUBLIC_TERMS_URL',
        'http://localhost/terms',
      ),
    /public HTTPS URL/,
  );
  assert.doesNotThrow(() =>
    validatePublicReleaseUrl(
      'production',
      'EXPO_PUBLIC_TERMS_URL',
      'https://www.example.com/terms',
    ),
  );
});

test('production accepts a valid HTTPS API URL', () => {
  assert.doesNotThrow(() =>
    validatePublicApiUrl('production', 'https://api.example.com/api/v1'),
  );
});
