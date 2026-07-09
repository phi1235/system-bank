export function decodeJwtPayload(token: string | null): Record<string, unknown> | null {
  if (!token) return null;
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function rolesFromToken(token: string | null): string[] {
  const p = decodeJwtPayload(token);
  if (!p) return [];
  const roles = p['roles'];
  if (Array.isArray(roles)) return roles.map(String);
  if (typeof roles === 'string') {
    try {
      const parsed = JSON.parse(roles);
      if (Array.isArray(parsed)) return parsed.map(String);
    } catch {
      return roles.split(',').map((s) => s.trim());
    }
  }
  return [];
}
