const isTunnel = typeof window !== 'undefined' && window.location.hostname.includes('phinguyenit.id.vn');

export const environment = {
  production: true,
  apiUrl: isTunnel ? 'https://api.phinguyenit.id.vn/api/v1' : '/api/v1',
  appName: 'Bank System',
};
