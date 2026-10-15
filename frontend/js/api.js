
const BACKEND = 'https://examallocate-backend.onrender.com';

const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
}[c]));

function note(text, bad = false) {
  const m = $('msg');
  if (!m) return;
  m.textContent = text;
  m.className = 'alert ' + (bad ? 'alert-error' : 'alert-success');
  m.classList.remove('hide');
}

async function api(path, { method = 'GET', body, adminKey } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (adminKey) headers['X-Admin-Key'] = adminKey;

  let res;
  try {
    res = await fetch(BACKEND + '/api' + path, {
      method, headers, body: body ? JSON.stringify(body) : undefined
    });
  } catch {
    const e = new Error('Unable to connect to the exam portal. Check that the backend is running.');
    note(e.message, true);
    throw e;
  }

  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }

  if (!res.ok) {
    const e = new Error((data && data.message) || 'Request failed (' + res.status + ')');
    note(e.message, true);
    throw e;
  }
  return data;
}

function navHtml() {
  return `
    <div class="topbar">
      <div class="topbar-inner">
        <a class="brand" href="index.html">
          <span class="brand-mark">N</span>
          <span><strong>NPTEL</strong><small>Examination Portal</small></span>
        </a>
        <nav>
          <a href="index.html">Home</a>
          <a href="apply.html">Exam Registration</a>
          <a href="status.html">My Registration</a>
          <a href="admin.html">Admin</a>
        </nav>
      </div>
    </div>`;
}

document.addEventListener('DOMContentLoaded', () => {
  const h = document.querySelector('header');
  if (h) h.innerHTML = navHtml();
});
