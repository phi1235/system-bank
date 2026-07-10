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
  return listClaimFromToken(token, 'roles');
}

export function permissionsFromToken(token: string | null): string[] {
  return listClaimFromToken(token, 'permissions');
}

function listClaimFromToken(token: string | null, claim: string): string[] {
  const p = decodeJwtPayload(token);
  if (!p) return [];
  const value = p[claim];
  if (Array.isArray(value)) return value.map(String);
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) return parsed.map(String);
    } catch {
      return value.split(',').map((s) => s.trim()).filter(Boolean);
    }
  }
  return [];
}
