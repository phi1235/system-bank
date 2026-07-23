const http = require('http');

const PORT = 4201;

const HTML_CONTENT = `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Dev Sandbox — Nhật ký OTP & SMS (Port 4201)</title>
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Fira+Code:wght@500;600&display=swap" rel="stylesheet" />
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet" />
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      background: #0f172a;
      color: #f1f5f9;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    header {
      background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
      border-bottom: 1px solid #1e3a5f;
      padding: 0.85rem 1.75rem;
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: 0 4px 20px rgba(0,0,0,0.4);
    }
    .header-inner {
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .logo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .logo-icon {
      font-size: 2rem;
      color: #38bdf8;
    }
    .logo-title {
      font-size: 1.2rem;
      font-weight: 800;
      color: #f8fafc;
      letter-spacing: -0.02em;
    }
    .logo-badge {
      font-size: 0.65rem;
      background: #0284c7;
      color: #ffffff;
      font-weight: 700;
      padding: 0.15rem 0.5rem;
      border-radius: 999px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .header-controls {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }
    .auto-refresh-group {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.82rem;
      color: #94a3b8;
    }
    select, input {
      background: #1e293b;
      color: #f1f5f9;
      border: 1px solid #334155;
      padding: 0.45rem 0.75rem;
      border-radius: 6px;
      font-size: 0.85rem;
      outline: none;
      transition: all 0.2s;
    }
    select:focus, input:focus {
      border-color: #38bdf8;
      box-shadow: 0 0 0 2px rgba(56,189,248,0.2);
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      background: transparent;
      color: #38bdf8;
      border: 1px solid #0284c7;
      padding: 0.45rem 0.9rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn:hover {
      background: rgba(56,189,248,0.12);
      border-color: #38bdf8;
    }
    .btn .material-icon { font-size: 1.1rem; }

    main {
      flex: 1;
      max-width: 1400px;
      width: 100%;
      margin: 0 auto;
      padding: 1.75rem 1.5rem;
    }

    .hero-banner {
      background: linear-gradient(135deg, rgba(30,58,95,0.6) 0%, rgba(15,23,42,0.8) 100%);
      border: 1px solid #1e3a5f;
      border-radius: 12px;
      padding: 1.25rem 1.5rem;
      margin-bottom: 1.5rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .hero-text h1 {
      font-size: 1.4rem;
      font-weight: 800;
      color: #f8fafc;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .hero-text p {
      font-size: 0.88rem;
      color: #94a3b8;
      margin-top: 0.3rem;
    }
    .port-tag {
      background: #0284c7;
      color: #fff;
      font-weight: 800;
      padding: 0.3rem 0.8rem;
      border-radius: 8px;
      font-size: 0.85rem;
      font-family: 'Fira Code', monospace;
    }

    .card {
      background: #1e293b;
      border: 1px solid #1e3a5f;
      border-radius: 12px;
      padding: 1.25rem;
      box-shadow: 0 4px 24px rgba(0,0,0,0.3);
    }

    .filters-bar {
      display: flex;
      gap: 1rem;
      margin-bottom: 1.25rem;
    }
    .search-input-wrap {
      flex: 1;
      position: relative;
    }
    .search-input-wrap .material-icon {
      position: absolute;
      left: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
      font-size: 1.2rem;
    }
    .search-input-wrap input {
      width: 100%;
      padding-left: 2.3rem;
    }

    .table-responsive {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }
    th {
      background: #0f172a;
      color: #64748b;
      font-size: 0.78rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #1e3a5f;
    }
    td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #1e3a5f;
      font-size: 0.88rem;
      color: #cbd5e1;
      vertical-align: middle;
    }
    tr:hover td {
      background: rgba(56,189,248,0.04);
    }

    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .chip.email { background: #0c4a6e; color: #38bdf8; }
    .chip.sms { background: #451a03; color: #fbbf24; }
    .chip.otp { background: #064e3b; color: #34d399; }
    .chip.ops { background: #3b0764; color: #c084fc; }

    .otp-box {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      background: #064e3b;
      color: #34d399;
      border: 1px solid #065f46;
      padding: 0.25rem 0.65rem;
      border-radius: 6px;
      font-size: 0.88rem;
      font-weight: 700;
      margin-bottom: 0.25rem;
    }
    .otp-box strong { font-size: 1.1rem; letter-spacing: 0.1em; font-family: 'Fira Code', monospace; }

    .template-badge {
      font-family: 'Fira Code', monospace;
      font-size: 0.78rem;
      background: #0f172a;
      color: #38bdf8;
      padding: 0.15rem 0.45rem;
      border-radius: 4px;
      border: 1px solid #1e3a5f;
    }

    .btn-copy {
      font-size: 0.78rem;
      padding: 0.3rem 0.65rem;
      color: #94a3b8;
      border-color: #334155;
    }
    .btn-copy:hover {
      color: #38bdf8;
      border-color: #38bdf8;
    }

    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 1rem;
      font-size: 0.85rem;
      color: #64748b;
    }
    .page-btns { display: flex; gap: 0.5rem; }
    .page-btns button {
      padding: 0.3rem 0.7rem;
      background: #0f172a;
      color: #e2e8f0;
      border: 1px solid #334155;
      border-radius: 6px;
      cursor: pointer;
    }
    .page-btns button:disabled { opacity: 0.4; cursor: not-allowed; }

    /* Toast */
    #toast {
      position: fixed;
      bottom: 2rem;
      right: 2rem;
      background: #0284c7;
      color: #fff;
      padding: 0.75rem 1.25rem;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.9rem;
      box-shadow: 0 4px 20px rgba(0,0,0,0.4);
      display: flex;
      align-items: center;
      gap: 0.5rem;
      opacity: 0;
      transform: translateY(20px);
      transition: all 0.3s;
      pointer-events: none;
      z-index: 1000;
    }
    #toast.show {
      opacity: 1;
      transform: translateY(0);
    }

    .empty-state {
      text-align: center;
      padding: 4rem 1rem;
      color: #64748b;
    }
    .empty-state .material-icon { font-size: 3.5rem; color: #1e3a5f; margin-bottom: 0.5rem; }

    footer {
      border-top: 1px solid #1e3a5f;
      text-align: center;
      padding: 0.85rem;
      font-size: 0.8rem;
      color: #475569;
      background: #0a1628;
    }
  </style>
</head>
<body>

  <header>
    <div class="header-inner">
      <div class="logo">
        <span class="material-icon logo-icon">terminal</span>
        <div>
          <span class="logo-title" id="txt-title">Dev Sandbox (SMS / OTP)</span>
        </div>
        <span class="logo-badge">STANDALONE · NO LOGIN REQUIRED</span>
      </div>

      <div class="header-controls">
        <div class="auto-refresh-group">
          <span class="material-icon" style="font-size: 1.1rem; color: #38bdf8;">sync</span>
          <span id="lbl-auto">Tự động làm mới:</span>
          <select id="auto-refresh-select" onchange="toggleAutoRefresh(this.value)">
            <option value="3000" selected>3s</option>
            <option value="5000">5s</option>
            <option value="10000">10s</option>
            <option value="0">Tắt (Off)</option>
          </select>
        </div>

        <button class="btn" onclick="fetchData()">
          <span class="material-icon">refresh</span>
          <span id="btn-refresh-text">Làm mới</span>
        </button>

        <select id="lang-select" onchange="changeLanguage(this.value)" style="margin-left: 0.5rem;">
          <option value="vi" selected>🇻🇳 Tiếng Việt</option>
          <option value="en">🇬🇧 English</option>
        </select>
      </div>
    </div>
  </header>

  <main>
    <div class="hero-banner">
      <div class="hero-text">
        <h1 id="banner-title">
          <span class="material-icon" style="color: #38bdf8;">mark_email_unread</span>
          Nhật ký OTP, SMS & Email — Cổng Độc Lập
        </h1>
        <p id="banner-sub">Trang công cụ độc lập chạy trên port 4201, không phụ thuộc vào login hệ thống. Dành riêng cho testing.</p>
      </div>
      <div class="port-tag">PORT 4201</div>
    </div>

    <div class="card">
      <div class="filters-bar">
        <div class="search-input-wrap">
          <span class="material-icon">search</span>
          <input type="text" id="search-input" placeholder="Tìm theo email, SĐT, mã OTP hoặc template..." oninput="onSearchInput()" />
        </div>

        <select id="channel-select" onchange="onChannelChange(this.value)" style="width: 200px;">
          <option value="ALL" id="opt-all">Tất cả kênh</option>
          <option value="EMAIL">EMAIL</option>
          <option value="SMS">SMS</option>
          <option value="OTP">OTP</option>
          <option value="OPS">OPS</option>
        </select>
      </div>

      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th id="th-channel">Kênh</th>
              <th id="th-recipient">Người nhận (Email / SĐT)</th>
              <th id="th-template">Mẫu thông báo</th>
              <th id="th-body">Nội dung / Mã OTP</th>
              <th id="th-time">Thời gian</th>
              <th id="th-actions" style="text-align: center;">Thao tác</th>
            </tr>
          </thead>
          <tbody id="table-body">
            <tr>
              <td colspan="6" class="empty-state">
                <span class="material-icon">sync</span>
                <p>Đang tải dữ liệu...</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <span id="pagination-info">Hiển thị 0 dòng</span>
        <div class="page-btns">
          <button id="btn-prev" onclick="prevPage()" disabled>&larr; Trước</button>
          <button id="btn-next" onclick="nextPage()" disabled>Sau &rarr;</button>
        </div>
      </div>
    </div>
  </main>

  <footer>
    🔒 Dev Sandbox Utility running independently on http://localhost:4201 · System Bank Project
  </footer>

  <div id="toast">
    <span class="material-icon">check_circle</span>
    <span id="toast-msg">Đã sao chép</span>
  </div>

  <script>
    let pageIndex = 0;
    let pageSize = 20;
    let totalElements = 0;
    let autoRefreshTimer = null;
    let currentLang = 'vi';

    const API_TARGETS = [
      '/api/sandbox',
      'http://localhost:8080/api/v1/dev/notifications/sandbox',
      'http://127.0.0.1:8080/api/v1/dev/notifications/sandbox'
    ];

    const I18N = {
      vi: {
        title: 'Dev Sandbox (SMS / OTP)',
        autoRefresh: 'Tự động làm mới:',
        refresh: 'Làm mới',
        bannerTitle: 'Nhật ký OTP, SMS & Email — Cổng Độc Lập',
        bannerSub: 'Trang công cụ độc lập chạy trên port 4201, không phụ thuộc vào login hệ thống. Dành riêng cho testing.',
        searchPh: 'Tìm theo email, SĐT, mã OTP hoặc template...',
        channelAll: 'Tất cả kênh',
        thChannel: 'Kênh',
        thRecipient: 'Người nhận (Email / SĐT)',
        thTemplate: 'Mẫu thông báo',
        thBody: 'Nội dung / Mã OTP',
        thTime: 'Thời gian',
        thActions: 'Thao tác',
        loading: 'Đang tải dữ liệu...',
        empty: 'Chưa có nhật ký Email / SMS / OTP nào.',
        copyOtp: 'Sao chép OTP',
        copyText: 'Sao chép',
        copiedOtp: 'Đã sao chép mã OTP: ',
        copiedBody: 'Đã sao chép nội dung thông báo',
        showing: 'Hiển thị {count} mục (Trang {page}/{totalPages})',
        prev: '← Trước',
        next: 'Sau →'
      },
      en: {
        title: 'Dev Sandbox (SMS / OTP)',
        autoRefresh: 'Auto Refresh:',
        refresh: 'Refresh',
        bannerTitle: 'OTP, SMS & Email Logs — Standalone Portal',
        bannerSub: 'Independent testing utility running on port 4201. No login required.',
        searchPh: 'Search by email, phone, OTP or template...',
        channelAll: 'All Channels',
        thChannel: 'Channel',
        thRecipient: 'Recipient (Email / Phone)',
        thTemplate: 'Template',
        thBody: 'Content / OTP',
        thTime: 'Time',
        thActions: 'Actions',
        loading: 'Loading data...',
        empty: 'No Email / SMS / OTP logs found.',
        copyOtp: 'Copy OTP',
        copyText: 'Copy',
        copiedOtp: 'Copied OTP: ',
        copiedBody: 'Copied content',
        showing: 'Showing {count} items (Page {page}/{totalPages})',
        prev: '← Prev',
        next: 'Next →'
      }
    };

    function changeLanguage(lang) {
      currentLang = lang;
      const t = I18N[lang];
      document.getElementById('txt-title').innerText = t.title;
      document.getElementById('lbl-auto').innerText = t.autoRefresh;
      document.getElementById('btn-refresh-text').innerText = t.refresh;
      document.getElementById('banner-title').innerHTML = '<span class="material-icon" style="color: #38bdf8;">mark_email_unread</span> ' + t.bannerTitle;
      document.getElementById('banner-sub').innerText = t.bannerSub;
      document.getElementById('search-input').placeholder = t.searchPh;
      document.getElementById('opt-all').innerText = t.channelAll;
      document.getElementById('th-channel').innerText = t.thChannel;
      document.getElementById('th-recipient').innerText = t.thRecipient;
      document.getElementById('th-template').innerText = t.thTemplate;
      document.getElementById('th-body').innerText = t.thBody;
      document.getElementById('th-time').innerText = t.thTime;
      document.getElementById('th-actions').innerText = t.thActions;
      fetchData();
    }

    async function fetchData() {
      const q = document.getElementById('search-input').value.trim();
      const channel = document.getElementById('channel-select').value;
      
      let urlParams = new URLSearchParams();
      urlParams.append('page', pageIndex);
      urlParams.append('size', pageSize);
      if (q) urlParams.append('q', q);
      if (channel && channel !== 'ALL') urlParams.append('channel', channel);

      let data = null;
      for (const baseUrl of API_TARGETS) {
        try {
          const res = await fetch(baseUrl + '?' + urlParams.toString());
          if (res.ok) {
            const json = await res.json();
            data = json.data || json;
            break;
          }
        } catch (e) {}
      }

      const tbody = document.getElementById('table-body');
      const t = I18N[currentLang];

      if (!data || !data.items || data.items.length === 0) {
        tbody.innerHTML = \`<tr><td colspan="6" class="empty-state">
          <span class="material-icon">mark_email_unread</span>
          <p>\${t.empty}</p>
        </td></tr>\`;
        document.getElementById('pagination-info').innerText = '0 items';
        document.getElementById('btn-prev').disabled = true;
        document.getElementById('btn-next').disabled = true;
        return;
      }

      totalElements = data.totalElements || data.items.length;
      const totalPages = Math.ceil(totalElements / pageSize) || 1;

      document.getElementById('pagination-info').innerText = t.showing
        .replace('{count}', totalElements)
        .replace('{page}', pageIndex + 1)
        .replace('{totalPages}', totalPages);

      document.getElementById('btn-prev').disabled = pageIndex <= 0;
      document.getElementById('btn-next').disabled = pageIndex >= totalPages - 1;

      tbody.innerHTML = data.items.map(item => {
        const otpMatch = item.body ? item.body.match(/\\b\\d{6,8}\\b|\\b[A-Za-z0-9]{8,12}\\b/) : null;
        const otp = otpMatch ? otpMatch[0] : null;
        const channelClass = (item.channel || '').toLowerCase();
        const icon = channelClass === 'email' ? 'email' : (channelClass === 'sms' ? 'sms' : (channelClass === 'ops' ? 'warning' : 'lock_clock'));
        
        const dateStr = item.createdAt ? new Date(item.createdAt).toLocaleString(currentLang === 'vi' ? 'vi-VN' : 'en-US') : '—';

        return \`
          <tr>
            <td>
              <span class="chip \${channelClass}">
                <span class="material-icon" style="font-size: 0.95rem;">\${icon}</span>
                \${item.channel}
              </span>
            </td>
            <td><strong>\${item.recipient || '—'}</strong></td>
            <td><span class="template-badge">\${item.template || '—'}</span></td>
            <td>
              \${otp ? \`<div class="otp-box"><span class="material-icon" style="font-size: 1rem;">key</span> OTP / MK: <strong>\${otp}</strong></div><br>\` : ''}
              <span>\${escapeHtml(item.body || '')}</span>
            </td>
            <td>\${dateStr}</td>
            <td style="text-align: center;">
              <button class="btn btn-copy" onclick="copyText('\${otp || escapeHtml(item.body)}', \${!!otp})">
                <span class="material-icon">content_copy</span>
                \${otp ? t.copyOtp : t.copyText}
              </button>
            </td>
          </tr>
        \`;
      }).join('');
    }

    function escapeHtml(str) {
      return (str || '').replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function copyText(text, isOtp) {
      navigator.clipboard.writeText(text).then(() => {
        const t = I18N[currentLang];
        showToast((isOtp ? t.copiedOtp : t.copiedBody) + (isOtp ? text : ''));
      });
    }

    function showToast(msg) {
      const toast = document.getElementById('toast');
      document.getElementById('toast-msg').innerText = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 3000);
    }

    let searchDebounce = null;
    function onSearchInput() {
      clearTimeout(searchDebounce);
      searchDebounce = setTimeout(() => {
        pageIndex = 0;
        fetchData();
      }, 300);
    }

    function onChannelChange() {
      pageIndex = 0;
      fetchData();
    }

    function prevPage() {
      if (pageIndex > 0) { pageIndex--; fetchData(); }
    }

    function nextPage() {
      pageIndex++;
      fetchData();
    }

    function toggleAutoRefresh(ms) {
      if (autoRefreshTimer) clearInterval(autoRefreshTimer);
      const val = parseInt(ms, 10);
      if (val > 0) {
        autoRefreshTimer = setInterval(fetchData, val);
      }
    }

    // Initial load + start 3s auto refresh
    fetchData();
    toggleAutoRefresh(3000);
  </script>
</body>
</html>`;

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/sandbox')) {
    const queryString = req.url.includes('?') ? req.url.substring(req.url.indexOf('?')) : '';
    const targetUrl = 'http://127.0.0.1:8080/api/v1/dev/notifications/sandbox' + queryString;
    
    http.get(targetUrl, (apiRes) => {
      let body = '';
      apiRes.on('data', chunk => body += chunk);
      apiRes.on('end', () => {
        res.writeHead(apiRes.statusCode || 200, {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        });
        res.end(body);
      });
    }).on('error', (err) => {
      res.writeHead(500, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      res.end(JSON.stringify({ success: false, error: { message: err.message } }));
    });
    return;
  }

  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Access-Control-Allow-Origin': '*'
  });
  res.end(HTML_CONTENT);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`\n=============================================================`);
  console.log(`🚀 STANDALONE DEV SANDBOX RUNNING ON PORT ${PORT}`);
  console.log(`👉 Open URL in browser: http://localhost:${PORT}`);
  console.log(`=============================================================\n`);
});
