const KEYCLOAK_ADMIN = process.env.KEYCLOAK_ADMIN ?? 'admin';
const KEYCLOAK_ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin';
const KEYCLOAK_SERVER = process.env.KEYCLOAK_SERVER ?? 'http://localhost:8080';
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM ?? 'test';
const KEYCLOAK_HTTP_CLIENT =
  process.env.KEYCLOAK_HTTP_CLIENT ?? 'java-app-web-api-server';
const KEYCLOAK_HTTPS_CLIENT =
  process.env.KEYCLOAK_HTTPS_CLIENT ?? 'java-app-web-api-server-secure';
const CLIENTS = [
  { id: KEYCLOAK_HTTP_CLIENT, appBaseUrl: 'http://localhost:8081' },
  { id: KEYCLOAK_HTTPS_CLIENT, appBaseUrl: 'https://localhost:8081' },
];

const credentials = async ({ server, user, password }) => {
  const response = await fetch(
    `${server}/realms/master/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        username: user,
        password,
        grant_type: 'password',
        client_id: 'admin-cli',
      }),
    }
  );
  const token = await response.json();
  if (token.error_description) {
    throw new Error(token.error_description);
  }
  return token.access_token;
};

const upsertClient = async (headers, { id, appBaseUrl }) => {
  const redirectUris = [`${appBaseUrl}/*`];
  const clientRepresentation = {
    attributes: {
      'backchannel.logout.revoke.offline.tokens': 'false',
      'backchannel.logout.session.required': 'true',
      'backchannel.logout.url': `${appBaseUrl}/logout/connect/back-channel/keycloak`,
      'jwks.url': `${appBaseUrl}/oauth2/jwks`,
      'post.logout.redirect.uris': redirectUris.join('##'),
      'use.jwks.url': 'true',
    },
    clientAuthenticatorType: 'client-jwt',
    clientId: id,
    description: '',
    directAccessGrantsEnabled: false,
    frontchannelLogout: false,
    name: '',
    protocol: 'openid-connect',
    publicClient: false,
    redirectUris,
    rootUrl: '',
    serviceAccountsEnabled: false,
    standardFlowEnabled: true,
    webOrigins: redirectUris,
  };
  const clientUrl = `${KEYCLOAK_SERVER}/admin/realms/${KEYCLOAK_REALM}/clients`;
  let response = await fetch(`${clientUrl}?clientId=${encodeURIComponent(id)}`, { headers });
  if (!response.ok) {
    const json = await response.json();
    console.error(`Failed to find client '${id}' (${response.status}): ${JSON.stringify(json)}`);
    return;
  }
  const [existingClient] = await response.json();
  const action = existingClient ? 'Updating' : 'Creating';
  console.info(`${action} client '${id}' for ${appBaseUrl}`);
  response = await fetch(existingClient ? `${clientUrl}/${existingClient.id}` : clientUrl, {
    method: existingClient ? 'PUT' : 'POST',
    headers,
    body: JSON.stringify(clientRepresentation),
  });
  if (!response.ok) {
    const json = await response.json();
    console.error(
      `Failed to ${action.toLowerCase()} client '${id}' (${response.status}): ${JSON.stringify(json)}`
    );
    return;
  }
  console.info(`${existingClient ? 'Updated' : 'Created'} client '${id}'`);
};

const setup = async () => {
  const bearer = await credentials({
    server: KEYCLOAK_SERVER,
    user: KEYCLOAK_ADMIN,
    password: KEYCLOAK_ADMIN_PASSWORD,
  });
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${bearer}`,
  };
  // Create realm
  console.info(`Creating realm '${KEYCLOAK_REALM}'`);
  let response = await fetch(`${KEYCLOAK_SERVER}/admin/realms`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      enabled: true,
      realm: KEYCLOAK_REALM,
      registrationAllowed: true,
    }),
  });
  let json = null;
  if (response.status === 409) {
    console.info(`Realm '${KEYCLOAK_REALM}' already exists`);
  } else if (response.status !== 201) {
    json = await response.json();
    console.error(
      `Failed to create realm '${KEYCLOAK_REALM}' (${response.status}): ${JSON.stringify(json)}`
    );
  } else {
    console.info(`Created realm '${KEYCLOAK_REALM}'`);
  }
  for (const client of CLIENTS) {
    await upsertClient(headers, client);
  }
};

setup()
  .catch((err) => console.error(err.message ?? err))
  .finally(() => console.info('Setup complete'));
