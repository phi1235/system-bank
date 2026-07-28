const isTunnel = typeof window !== 'undefined' && window.location.hostname.includes('phinguyenit.id.vn');

export const environment = {
  production: false,
  apiUrl: isTunnel ? 'https://api.phinguyenit.id.vn/api/v1' : 'http://localhost:8080/api/v1',
  appName: 'Bank System',
};
