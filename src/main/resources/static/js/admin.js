/**
 * MakeCall Admin Panel — vanilla JS + jQuery (matches quickstart.js conventions).
 * All data is loaded client-side from /admin/api/**, which is protected server-side
 * by AdminAuthInterceptor (session-based, requires isAdmin=true and not blocked).
 */
(function () {
  'use strict';

  var currentSection = 'dashboard';
  var confirmCallback = null;

  document.addEventListener('DOMContentLoaded', function () {
    wireNav();
    wireDashboard();
    wireUsers();
    wireWallets();
    wireTransactions();
    wireModals();
    loadGreeting();
    loadDashboard();
  });

  // ─── Greeting (admin identity, from the previously-unused /admin/api/me) ──

  function loadGreeting() {
    var el = document.getElementById('admin-greeting');
    if (!el) return;
    $.ajax({
      url: '/admin/api/me',
      method: 'GET',
      dataType: 'json',
      success: function (admin) {
        var label = admin.name || admin.number || admin.email || 'Admin';
        el.textContent = 'Hey, ' + label + '! 👋';
      },
      error: function () {
        el.textContent = 'Admin Panel';
      }
    });
  }

  // ─── Navigation (single-page section switching) ───────────────────────────

  function wireNav() {
    var links = document.querySelectorAll('.admin-nav-link[data-section]');
    links.forEach(function (link) {
      link.addEventListener('click', function (e) {
        e.preventDefault();
        var section = link.getAttribute('data-section');
        switchSection(section);
      });
    });
  }

  function switchSection(section) {
    currentSection = section;
    document.querySelectorAll('.admin-section').forEach(function (el) {
      el.classList.toggle('is-active', el.id === 'admin-section-' + section);
    });
    document.querySelectorAll('.admin-nav-link[data-section]').forEach(function (link) {
      link.classList.toggle('is-active', link.getAttribute('data-section') === section);
    });
    if (section === 'dashboard') loadDashboard();
    if (section === 'users') loadUsers();
    if (section === 'wallets') loadWallets();
    if (section === 'transactions') loadTransactions();
  }

  // ─── Dashboard ──────────────────────────────────────────────────────────────

  function wireDashboard() {
    var btn = document.getElementById('btn-refresh-dashboard');
    if (btn) btn.addEventListener('click', function () {
      btn.classList.remove('is-spinning');
      requestAnimationFrame(function () { btn.classList.add('is-spinning'); });
      loadDashboard();
    });
  }

  function loadDashboard() {
    setState('dashboard', 'loading');
    $.ajax({
      url: '/admin/api/dashboard',
      method: 'GET',
      dataType: 'json',
      success: function (stats) {
        setText('stat-total-users', stats.totalUsers);
        setText('stat-active-users', stats.activeUsers);
        setText('stat-blocked-users', stats.blockedUsers);
        setText('stat-wallet-count', stats.walletCount);
        setText('stat-wallet-balance', formatCurrency(stats.totalWalletBalance));
        setText('stat-total-transactions', stats.totalTransactions);
        setText('stat-credit-transactions', stats.creditTransactions);
        setText('stat-debit-transactions', stats.debitTransactions);
        setState('dashboard', 'ready');
      },
      error: function (err) {
        handleAuthError(err);
        setState('dashboard', 'error', readError(err, 'Could not load dashboard stats.'));
      }
    });
  }

  // ─── Users ──────────────────────────────────────────────────────────────────

  function wireUsers() {
    var btn = document.getElementById('btn-apply-user-filter');
    if (btn) btn.addEventListener('click', loadUsers);
    var search = document.getElementById('user-search');
    if (search) search.addEventListener('keydown', function (e) { if (e.key === 'Enter') loadUsers(); });
  }

  function loadUsers() {
    setState('users', 'loading');
    var search = valueOf('user-search');
    var status = valueOf('user-status-filter');
    $.ajax({
      url: '/admin/api/users',
      method: 'GET',
      data: { search: search, status: status },
      dataType: 'json',
      success: function (users) {
        renderUsersTable(users);
        setState('users', users.length ? 'ready' : 'empty');
      },
      error: function (err) {
        handleAuthError(err);
        setState('users', 'error', readError(err, 'Could not load users.'));
      }
    });
  }

  function renderUsersTable(users) {
    var body = document.getElementById('users-table-body');
    body.innerHTML = users.map(function (u) {
      var statusBadge = u.blocked
        ? '<span class="status-pill status-error">Blocked</span>'
        : '<span class="status-pill status-ready">Active</span>';
      var toggleBtn = u.blocked
        ? '<button type="button" class="row-action-btn row-action-success" data-action="unblock" data-id="' + escapeHtml(u.id) + '">Unblock</button>'
        : '<button type="button" class="row-action-btn row-action-danger" data-action="block" data-id="' + escapeHtml(u.id) + '">Block</button>';
      return '<tr>' +
        '<td class="cell-wrap cell-name" data-label="Name"><span class="name-cell">' + avatarBadge(u.name, u.id) + '<span>' + escapeHtml(u.name || '—') + '</span></span></td>' +
        '<td data-label="Number">' + escapeHtml(u.number || '—') + '</td>' +
        '<td class="cell-wrap" data-label="Email">' + escapeHtml(u.email || '—') + '</td>' +
        '<td data-label="Wallet">' + formatCurrency(u.walletBalance) + '</td>' +
        '<td data-label="Status">' + statusBadge + '</td>' +
        '<td data-label="Joined">' + formatDate(u.createdAt) + '</td>' +
        '<td class="cell-actions" data-label="Actions">' +
          '<button type="button" class="row-action-btn" data-action="view" data-id="' + escapeHtml(u.id) + '">View</button>' +
          toggleBtn +
        '</td>' +
      '</tr>';
    }).join('');

    body.querySelectorAll('[data-action="view"]').forEach(function (btn) {
      btn.addEventListener('click', function () { viewUserDetail(btn.getAttribute('data-id')); });
    });
    body.querySelectorAll('[data-action="block"]').forEach(function (btn) {
      btn.addEventListener('click', function () { confirmBlockUser(btn.getAttribute('data-id')); });
    });
    body.querySelectorAll('[data-action="unblock"]').forEach(function (btn) {
      btn.addEventListener('click', function () { confirmUnblockUser(btn.getAttribute('data-id')); });
    });
  }

  function confirmBlockUser(userId) {
    showConfirm('Block this user? They will not be able to sign in or make calls until unblocked.', function () {
      $.ajax({
        url: '/admin/api/users/' + encodeURIComponent(userId) + '/block',
        method: 'POST',
        success: function () {
          showToast('User blocked.', 'success');
          loadUsers();
        },
        error: function (err) {
          handleAuthError(err);
          showToast(readError(err, 'Could not block user.'), 'error');
        }
      });
    });
  }

  function confirmUnblockUser(userId) {
    showConfirm('Unblock this user? They will be able to sign in and make calls again.', function () {
      $.ajax({
        url: '/admin/api/users/' + encodeURIComponent(userId) + '/unblock',
        method: 'POST',
        success: function () {
          showToast('User unblocked.', 'success');
          loadUsers();
        },
        error: function (err) {
          handleAuthError(err);
          showToast(readError(err, 'Could not unblock user.'), 'error');
        }
      });
    });
  }

  function viewUserDetail(userId) {
    $.ajax({
      url: '/admin/api/users/' + encodeURIComponent(userId),
      method: 'GET',
      dataType: 'json',
      success: function (detail) {
        renderUserDetail(detail);
        openModal('user-detail-modal');
      },
      error: function (err) {
        handleAuthError(err);
        showToast(readError(err, 'Could not load user details.'), 'error');
      }
    });
  }

  function renderUserDetail(detail) {
    var u = detail.user;
    document.getElementById('user-detail-title').textContent = u.name || u.number || u.id;

    var transactionsHtml = (detail.recentTransactions || []).map(function (t) {
      return '<div class="user-detail-list-item">' +
        '<span>' + escapeHtml(t.type) + ' &mdash; ' + escapeHtml(t.reason || '') + '</span>' +
        '<strong>' + formatCurrency(t.amount) + '</strong>' +
      '</div>';
    }).join('') || '<p class="empty-state">No wallet transactions yet.</p>';

    var callsHtml = (detail.recentCalls || []).map(function (c) {
      return '<div class="user-detail-list-item">' +
        '<span>' + escapeHtml(c.destination || 'unknown') + ' &mdash; ' + escapeHtml(c.status || '') + '</span>' +
        '<strong>' + formatCurrency(c.amountCharged) + '</strong>' +
      '</div>';
    }).join('') || '<p class="empty-state">No calls yet.</p>';

    document.getElementById('user-detail-body').innerHTML =
      '<div class="user-detail-grid">' +
        '<div>Number<strong>' + escapeHtml(u.number || '—') + '</strong></div>' +
        '<div>Email<strong>' + escapeHtml(u.email || '—') + '</strong></div>' +
        '<div>Wallet Balance<strong>' + formatCurrency(u.walletBalance) + '</strong></div>' +
        '<div>Status<strong>' + (u.blocked ? 'Blocked' : 'Active') + '</strong></div>' +
        '<div>Joined<strong>' + formatDate(u.createdAt) + '</strong></div>' +
        '<div>User ID<strong>' + escapeHtml(u.id) + '</strong></div>' +
      '</div>' +
      '<p class="user-detail-subheading">Recent Wallet Transactions</p>' +
      '<div class="user-detail-list">' + transactionsHtml + '</div>' +
      '<p class="user-detail-subheading">Recent Calls</p>' +
      '<div class="user-detail-list">' + callsHtml + '</div>';
  }

  // ─── Wallets ────────────────────────────────────────────────────────────────

  function wireWallets() {
    var btn = document.getElementById('btn-apply-wallet-filter');
    if (btn) btn.addEventListener('click', loadWallets);
    var search = document.getElementById('wallet-search');
    if (search) search.addEventListener('keydown', function (e) { if (e.key === 'Enter') loadWallets(); });
  }

  function loadWallets() {
    setState('wallets', 'loading');
    $.ajax({
      url: '/admin/api/wallets',
      method: 'GET',
      data: { search: valueOf('wallet-search') },
      dataType: 'json',
      success: function (users) {
        renderWalletsTable(users);
        setState('wallets', users.length ? 'ready' : 'empty');
      },
      error: function (err) {
        handleAuthError(err);
        setState('wallets', 'error', readError(err, 'Could not load wallets.'));
      }
    });
  }

  function renderWalletsTable(users) {
    var body = document.getElementById('wallets-table-body');
    body.innerHTML = users.map(function (u) {
      var statusBadge = u.blocked
        ? '<span class="status-pill status-error">Blocked</span>'
        : '<span class="status-pill status-ready">Active</span>';
      return '<tr>' +
        '<td class="cell-wrap cell-name" data-label="User"><span class="name-cell">' + avatarBadge(u.name, u.id) + '<span>' + escapeHtml(u.name || u.id) + '</span></span></td>' +
        '<td data-label="Number">' + escapeHtml(u.number || '—') + '</td>' +
        '<td data-label="Balance">' + formatCurrency(u.walletBalance) + '</td>' +
        '<td data-label="Reward">' + (u.walletRewardCredited ? 'Credited' : 'Not credited') + '</td>' +
        '<td data-label="Status">' + statusBadge + '</td>' +
      '</tr>';
    }).join('');
  }

  // ─── Transactions ───────────────────────────────────────────────────────────

  function wireTransactions() {
    var btn = document.getElementById('btn-apply-tx-filter');
    if (btn) btn.addEventListener('click', loadTransactions);
  }

  function loadTransactions() {
    setState('tx', 'loading');
    $.ajax({
      url: '/admin/api/transactions',
      method: 'GET',
      data: {
        userId: valueOf('tx-user-id'),
        type: valueOf('tx-type-filter'),
        from: valueOf('tx-from'),
        to: valueOf('tx-to')
      },
      dataType: 'json',
      success: function (transactions) {
        renderTransactionsTable(transactions);
        setState('tx', transactions.length ? 'ready' : 'empty');
      },
      error: function (err) {
        handleAuthError(err);
        setState('tx', 'error', readError(err, 'Could not load transactions.'));
      }
    });
  }

  function renderTransactionsTable(transactions) {
    var body = document.getElementById('tx-table-body');
    body.innerHTML = transactions.map(function (t) {
      var typeBadge = t.type === 'CREDIT'
        ? '<span class="status-pill status-ready">Credit</span>'
        : '<span class="status-pill status-error">Debit</span>';
      return '<tr>' +
        '<td data-label="Date">' + formatDate(t.createdAt) + '</td>' +
        '<td data-label="User ID">' + escapeHtml(t.userId || '—') + '</td>' +
        '<td data-label="Type">' + typeBadge + '</td>' +
        '<td data-label="Amount">' + formatCurrency(t.amount) + '</td>' +
        '<td data-label="Balance After">' + formatCurrency(t.balanceAfter) + '</td>' +
        '<td class="cell-wrap" data-label="Reason">' + escapeHtml(t.reason || '') + '</td>' +
      '</tr>';
    }).join('');
  }

  // ─── Modals ─────────────────────────────────────────────────────────────────

  function wireModals() {
    bindClose('btn-close-user-detail', 'user-detail-modal');
    bindClose('btn-close-user-detail-x', 'user-detail-modal');
    bindClose('btn-confirm-cancel', 'confirm-modal');

    var confirmYes = document.getElementById('btn-confirm-yes');
    if (confirmYes) {
      confirmYes.addEventListener('click', function () {
        var cb = confirmCallback;
        closeModal('confirm-modal');
        if (typeof cb === 'function') cb();
      });
    }

    document.querySelectorAll('.modal-backdrop').forEach(function (modal) {
      modal.addEventListener('click', function (e) {
        if (e.target === modal) closeModal(modal.id);
      });
    });
  }

  function bindClose(btnId, modalId) {
    var btn = document.getElementById(btnId);
    if (btn) btn.addEventListener('click', function () { closeModal(modalId); });
  }

  function showConfirm(message, onConfirm) {
    document.getElementById('confirm-message').textContent = message;
    confirmCallback = onConfirm;
    openModal('confirm-modal');
  }

  function openModal(id) {
    var modal = document.getElementById(id);
    if (modal) modal.classList.add('open');
  }

  function closeModal(id) {
    var modal = document.getElementById(id);
    if (modal) modal.classList.remove('open');
  }

  // ─── State helpers (loading / error / empty / ready) ───────────────────────

  function setState(prefix, state, message) {
    var loading = document.getElementById(prefix + '-loading');
    var error = document.getElementById(prefix + '-error');
    var empty = document.getElementById(prefix + '-empty');
    var content = document.getElementById(prefix === 'dashboard' ? 'dashboard-stats' : prefix + '-table-wrap');
    var contentDisplay = prefix === 'dashboard' ? 'grid' : 'block';

    if (loading) loading.style.display = state === 'loading' ? 'block' : 'none';
    if (error) {
      error.style.display = state === 'error' ? 'block' : 'none';
      if (state === 'error') error.textContent = message || 'Something went wrong.';
    }
    if (empty) empty.style.display = state === 'empty' ? 'block' : 'none';
    if (content) content.style.display = state === 'ready' ? contentDisplay : 'none';
  }

  // ─── Auth error handling ───────────────────────────────────────────────────

  function handleAuthError(err) {
    if (err && err.status === 401) {
      showToast('Your session has expired. Redirecting to sign in…', 'error');
      setTimeout(function () { window.location.href = '/'; }, 1500);
    } else if (err && err.status === 403) {
      showToast('Access denied. Redirecting…', 'error');
      setTimeout(function () { window.location.href = '/'; }, 1500);
    }
  }

  // ─── Formatting helpers ─────────────────────────────────────────────────────

  function formatCurrency(value) {
    var n = typeof value === 'number' ? value : parseFloat(value || 0);
    if (isNaN(n)) n = 0;
    return '₹' + n.toFixed(2);
  }

  function formatDate(value) {
    if (!value) return '—';
    var d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    return d.toLocaleString();
  }

  function valueOf(id) {
    var el = document.getElementById(id);
    return el ? el.value.trim() : '';
  }

  function setText(id, value) {
    var el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  /**
   * Colored initials avatar. Color is deterministic per user (based on a simple
   * char-code hash of the id/name), so the same user always gets the same color.
   */
  function avatarBadge(name, id) {
    var label = (name || id || '?').trim();
    var initial = label.charAt(0).toUpperCase() || '?';
    var seedSource = (id || label || '');
    var seed = 0;
    for (var i = 0; i < seedSource.length; i++) seed += seedSource.charCodeAt(i);
    var colorIndex = seed % 6;
    return '<span class="avatar-badge avatar-' + colorIndex + '">' + escapeHtml(initial) + '</span>';
  }

  // ─── Shared helpers (mirrors quickstart.js so the two UIs feel identical) ──

  function showToast(message, type, duration) {
    var container = document.getElementById('toast-container');
    if (!container) return;
    var icons = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };
    var icon = icons[type] || 'ℹ';
    var toast = document.createElement('div');
    toast.className = 'toast toast-' + (type || 'info');
    toast.innerHTML = '<span class="toast-icon">' + icon + '</span><span>' + escapeHtml(message) + '</span>';
    container.appendChild(toast);
    var timeout = duration || (type === 'error' ? 5000 : 3500);
    setTimeout(function () {
      toast.classList.add('toast-hiding');
      setTimeout(function () { if (toast.parentNode) toast.remove(); }, 350);
    }, timeout);
  }

  function readError(error, fallback) {
    if (error && error.responseJSON) {
      if (error.responseJSON.message) return error.responseJSON.message;
      if (error.responseJSON.error) return error.responseJSON.error;
    }
    if (error && error.responseText) {
      try {
        var parsed = JSON.parse(error.responseText);
        return parsed.message || parsed.error || fallback;
      } catch (ignored) {}
      return error.responseText.substring(0, 200);
    }
    if (error && error.status === 401) return 'Session expired. Please sign in again.';
    if (error && error.status === 403) return 'Access denied.';
    return fallback;
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
})();
