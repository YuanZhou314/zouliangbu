/*
 * 走两步 · 管理后台公共外壳
 *
 * 为什么不用框架（Vue/React）：后台是内部工具，7 个页面的交互复杂度很低，
 * 引入构建链反而让"改一行字要重新 build"成为负担。
 * 这里只封装三件事：左菜单外壳、鉴权守卫、统一请求。
 *
 * 用法（每个页面）：
 *   <body>
 *     <div id="app"></div>
 *     <script src="layout.js"></script>
 *     <script>Shell.mount({active:'routes', title:'线路库', render(root){ ... }});</script>
 *   </body>
 */

const Shell = (function () {
  // ---- 菜单配置：加一个模块只改这一处 ----
  const MENU = [
    {
      group: '概览',
      items: [{ key: 'dashboard', name: '数据看板', href: 'dashboard.html' }]
    },
    {
      group: '内容运营',
      items: [
        { key: 'routes', name: '线路库', href: 'index.html' },
        { key: 'gear', name: '装备分类', href: 'gear.html' }
      ]
    },
    {
      group: '审核治理',
      items: [
        { key: 'proposals', name: '社区提案', href: 'proposals.html' },
        { key: 'audit', name: '操作审计', href: 'audit.html' }
      ]
    },
    {
      group: '系统',
      items: [{ key: 'config', name: '配置中心', href: 'config.html' }]
    }
  ];

  /** token 存取。登录页与各功能页共用同一把钥匙。 */
  function getToken() {
    return localStorage.getItem('admin_token') || '';
  }

  function clearToken() {
    localStorage.removeItem('admin_token');
  }

  /** 401 直接踢回登录页 —— 每个请求都要检查，否则页面会停在"加载中"让人以为卡死。 */
  function gotoLogin() {
    clearToken();
    window.location.href = 'login.html';
  }

  /**
   * 统一请求。
   * 约定：服务端成功返回 {code:0,data:...}，失败返回非 0 且 HTTP 状态码跟着变。
   * 所以这里两处都判，任一失败就抛错，调用方只处理"成功"分支。
   */
  async function api(path, opts) {
    opts = opts || {};
    opts.headers = opts.headers || {};
    opts.headers['X-Admin-Token'] = getToken();
    if (opts.body && !opts.headers['Content-Type'] && !(opts.body instanceof FormData)) {
      opts.headers['Content-Type'] = 'application/json';
    }
    const resp = await fetch(path, opts);
    if (resp.status === 401) {
      gotoLogin();
      throw new Error('未登录');
    }
    let body = null;
    try {
      body = await resp.json();
    } catch (e) {
      throw new Error('服务端返回异常（HTTP ' + resp.status + '）');
    }
    if (!resp.ok || body.code !== 0) {
      throw new Error(body.msg || ('请求失败 HTTP ' + resp.status));
    }
    return body.data;
  }

  /** HTML 转义。所有拼进 innerHTML 的动态内容都必须过一遍，否则一条含 <script> 的动态就能打穿后台。 */
  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s).replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  }

  /** 轻量 toast，替代 alert（alert 会阻塞且样式丑）。 */
  function toast(msg, ok) {
    let el = document.getElementById('__toast');
    if (!el) {
      el = document.createElement('div');
      el.id = '__toast';
      el.className = 'toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.className = 'toast show' + (ok === false ? ' err' : '');
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.className = 'toast'; }, 2200);
  }

  function renderMenu(active) {
    return MENU.map(g => `
      <div class="nav-group">${esc(g.group)}</div>
      ${g.items.map(it => `
        <a class="nav-item${it.key === active ? ' active' : ''}" href="${it.href}">${esc(it.name)}</a>
      `).join('')}
    `).join('');
  }

  function mount(cfg) {
    if (!getToken()) {
      gotoLogin();
      return;
    }
    const app = document.getElementById('app');
    app.innerHTML = `
      <aside class="sidebar">
        <div class="brand">走两步<small>管理后台</small></div>
        <nav>${renderMenu(cfg.active)}</nav>
        <div class="sidebar-foot"><button id="__logout">退出登录</button></div>
      </aside>
      <main class="main">
        <header class="page-head">
          <h1>${esc(cfg.title || '')}</h1>
          <div class="head-actions" id="__headActions"></div>
        </header>
        <section class="page-body" id="__body"></section>
      </main>
    `;
    document.getElementById('__logout').onclick = async function () {
      try { await api('/api/v1/admin/logout', { method: 'POST' }); } catch (e) { /* 忽略 */ }
      gotoLogin();
    };
    cfg.render(document.getElementById('__body'), {
      api: api,
      esc: esc,
      toast: toast,
      actions: document.getElementById('__headActions'),
      title: (t) => { document.querySelector('.page-head h1').textContent = t; }
    });
  }

  return { mount, api, esc, toast, getToken, gotoLogin };
})();
