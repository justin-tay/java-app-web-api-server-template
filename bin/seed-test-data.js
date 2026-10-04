const KEYCLOAK_ADMIN = process.env.KEYCLOAK_ADMIN ?? 'admin';
const KEYCLOAK_ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin';
const KEYCLOAK_SERVER = process.env.KEYCLOAK_SERVER ?? 'http://localhost:8080';
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM ?? 'test';
// The usernames are the OIDC preferred_username and match the local users that
// development-seed.sql creates. First name,
// last name and email are set so Keycloak does not ask the user to complete their profile.
const TEST_USERS = [
  { username: 'admin', firstName: 'Alan', lastName: 'Tan' },
  { username: 'user', firstName: 'Mary', lastName: 'Goh' },
  { username: 'multi-group-user', firstName: 'Grace', lastName: 'Lee' },
  { username: 'account-reviewer-1', firstName: 'Rachel', lastName: 'Lim' },
  { username: 'account-reviewer-2', firstName: 'Ravi', lastName: 'Nair' },
];

const credentials = async () => {
  const response = await fetch(`${KEYCLOAK_SERVER}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      username: KEYCLOAK_ADMIN,
      password: KEYCLOAK_ADMIN_PASSWORD,
      grant_type: 'password',
      client_id: 'admin-cli',
    }),
  });
  const token = await response.json();
  if (!response.ok) throw new Error(token.error_description ?? 'Unable to authenticate to Keycloak');
  return token.access_token;
};

const seedUser = async (headers, { username, firstName, lastName }) => {
  const baseUrl = `${KEYCLOAK_SERVER}/admin/realms/${KEYCLOAK_REALM}/users`;
  const existing = await fetch(`${baseUrl}?username=${encodeURIComponent(username)}&exact=true`, { headers });
  if (!existing.ok) throw new Error(`Unable to query '${username}'`);
  if ((await existing.json()).length) {
    console.info(`Test user '${username}' already exists`);
    return;
  }
  const response = await fetch(baseUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      enabled: true,
      username,
      email: `${username}@example.test`,
      firstName,
      lastName,
      credentials: [{ type: 'password', value: 'password', temporary: false }],
    }),
  });
  if (!response.ok) throw new Error(`Unable to create '${username}'`);
  console.info(`Created test user '${username}'`);
};

const seed = async () => {
  const bearer = await credentials();
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${bearer}` };
  for (const user of TEST_USERS) await seedUser(headers, user);
};

seed().catch((error) => {
  console.error(error.message ?? error);
  process.exitCode = 1;
});
