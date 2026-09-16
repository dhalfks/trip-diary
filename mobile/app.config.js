const baseConfig = require('./app.json');

const LOCAL_HOSTNAMES = new Set(['localhost', '127.0.0.1', '0.0.0.0', '10.0.2.2', '::1']);

function validatePublicApiUrl(appEnvironment, apiBaseUrl) {
  if (appEnvironment !== 'production') {
    return;
  }

  if (!apiBaseUrl) {
    throw new Error(
      'EXPO_PUBLIC_API_BASE_URL must be configured in the EAS production environment.',
    );
  }

  let url;
  try {
    url = new URL(apiBaseUrl);
  } catch {
    throw new Error('EXPO_PUBLIC_API_BASE_URL must be a valid absolute URL.');
  }

  if (url.protocol !== 'https:' || LOCAL_HOSTNAMES.has(url.hostname)) {
    throw new Error(
      'Production EXPO_PUBLIC_API_BASE_URL must use HTTPS and must not point to localhost.',
    );
  }
}

function validatePublicReleaseUrl(appEnvironment, name, value) {
  if (appEnvironment !== 'production') {
    return;
  }

  if (!value) {
    throw new Error(`${name} must be configured in the EAS production environment.`);
  }

  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`${name} must be a valid absolute URL.`);
  }

  if (
    url.protocol !== 'https:' ||
    LOCAL_HOSTNAMES.has(url.hostname) ||
    url.username ||
    url.password
  ) {
    throw new Error(`${name} must be a public HTTPS URL without credentials.`);
  }
}

module.exports = ({ config }) => {
  const appEnvironment = process.env.EXPO_PUBLIC_APP_ENV ?? 'development';
  validatePublicApiUrl(appEnvironment, process.env.EXPO_PUBLIC_API_BASE_URL);
  validatePublicReleaseUrl(
    appEnvironment,
    'EXPO_PUBLIC_PRIVACY_POLICY_URL',
    process.env.EXPO_PUBLIC_PRIVACY_POLICY_URL,
  );
  validatePublicReleaseUrl(
    appEnvironment,
    'EXPO_PUBLIC_TERMS_URL',
    process.env.EXPO_PUBLIC_TERMS_URL,
  );

  return {
    ...baseConfig.expo,
    ...config,
    extra: {
      ...baseConfig.expo.extra,
      ...config.extra,
      appEnvironment,
    },
  };
};

module.exports.validatePublicApiUrl = validatePublicApiUrl;
module.exports.validatePublicReleaseUrl = validatePublicReleaseUrl;
