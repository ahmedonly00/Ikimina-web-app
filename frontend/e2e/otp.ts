import { execFileSync } from 'node:child_process';

/**
 * Reads the newest one-time code sent to `phone` from the backend's log. Only the dev
 * profile's fake SMS provider writes codes there; nothing else in the system logs them.
 *
 * Configure with E2E_COMPOSE_PROJECT, E2E_COMPOSE_FILE and E2E_COMPOSE_ENV_FILE.
 */
export function latestOtp(phone: string): string {
  const args = ['compose', '-p', process.env.E2E_COMPOSE_PROJECT ?? 'ikimina-e2e'];
  if (process.env.E2E_COMPOSE_FILE) {
    args.push('-f', process.env.E2E_COMPOSE_FILE);
  }
  if (process.env.E2E_COMPOSE_ENV_FILE) {
    args.push('--env-file', process.env.E2E_COMPOSE_ENV_FILE);
  }
  args.push('logs', '--no-color', 'backend');
  const log = execFileSync('docker', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  const pattern = new RegExp(`fake SMS to \\${phone}\\] .*?([0-9]{6})`, 'g');
  const codes = [...log.matchAll(pattern)].map((match) => match[1]);
  const code = codes.at(-1);
  if (!code) {
    throw new Error(`No one-time code for ${phone} in the backend log`);
  }
  return code;
}
