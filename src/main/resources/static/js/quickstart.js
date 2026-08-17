$(function () {
  // ─── State ──────────────────────────────────────────────────────────────
  let device;
  let activeCall;
  let callRingTime    = null;  // ms – set when ringing starts (total duration reference)
  let callAnswerTime  = null;  // ms – set when call is answered (billing starts here)
  let callTimerInterval = null;
  let wakeLockSentinel    = null;  // Screen Wake Lock sentinel (keeps screen on during calls)
  let currentDestination  = '';
  let currentUser;
  let wallet;
  let pendingDestination  = '';    // queued before auth completes
  let firebaseAuth;

  // Cookie names
  const COOKIE_UID           = 'pb_uid';
  const COOKIE_NAME          = 'pb_name';
  const COOKIE_EMAIL         = 'pb_email';
  const COOKIE_IDENTITY      = 'pb_identity';
  const COOKIE_NEEDS_PROFILE = 'pb_needs_profile';
  const COOKIE_TTL_DAYS      = 360;

  // ─── DOM refs ───────────────────────────────────────────────────────────
  const speakerDevices    = document.getElementById('speaker-devices');
  const ringtoneDevices   = document.getElementById('ringtone-devices');
  const outputVolumeBar   = document.getElementById('output-volume');
  const inputVolumeBar    = document.getElementById('input-volume');
  const volumeIndicators  = document.getElementById('volume-indicators');
  const phoneNumberInput  = document.getElementById('phone-number');
  const dialpad           = document.getElementById('dialpad');
  const clearButton       = document.getElementById('button-clear');
  const deviceStatus      = document.getElementById('device-status');
  const walletBalance     = document.getElementById('wallet-balance');
  const walletRate        = document.getElementById('wallet-rate');
  const topUpButton       = document.getElementById('button-topup');
  const logoutButton      = document.getElementById('button-logout');
  const historyList       = document.getElementById('history-list');
  const callTimerEl       = document.getElementById('call-timer');

  // Google auth modal
  const googleAuthModal   = document.getElementById('google-auth-modal');
  const btnGoogleSignin   = document.getElementById('btn-google-signin');
  const btnCloseAuth      = document.getElementById('btn-close-auth');

  // Simple login modal (used when enableGoogleSignIn = false)
  const simpleLoginModal      = document.getElementById('simple-login-modal');
  const simpleLoginForm       = document.getElementById('simple-login-form');
  const simpleLoginNumber     = document.getElementById('simple-login-number');
  const simpleLoginLocationEl = document.getElementById('simple-login-location');
  const btnCloseSimpleLogin   = document.getElementById('btn-close-simple-login');

  // Firebase setup notice
  const firebaseSetupNotice = document.getElementById('firebase-setup-notice');
  const btnCloseSetupNotice = document.getElementById('btn-close-setup-notice');

  // Profile modal
  const profileModal  = document.getElementById('profile-modal');
  const profileForm   = document.getElementById('profile-form');
  const profilePhone  = document.getElementById('profile-phone');
  const profileLocation = document.getElementById('profile-location');
  const profileTerms  = document.getElementById('profile-terms');

  // Terms inline modal
  const termsModal  = document.getElementById('terms-modal');
  const closeTerms  = document.getElementById('close-terms');
  const acceptTerms = document.getElementById('accept-terms');

  // Recharge modal
  const rechargeModal     = document.getElementById('recharge-modal');
  const rechargeForm      = document.getElementById('recharge-form');
  const rechargeAmountEl  = document.getElementById('recharge-amount');
  const rechargeGatewayEl = 'razorpaycashfree'; //document.getElementById('recharge-gateway');
  const btnRechargeConfirm = document.getElementById('btn-recharge-confirm');
  const btnRechargeCancel  = document.getElementById('btn-recharge-cancel');

  // Permissions modal
  const permissionsModal      = document.getElementById('permissions-modal');
  const btnCheckPermissions   = document.getElementById('btn-check-permissions');
  const btnRequestMic         = document.getElementById('btn-request-mic');
  const btnClosePermissions   = document.getElementById('btn-close-permissions');
  const btnClosePermissionsX  = document.getElementById('btn-close-permissions-x');
  const permMicBadge          = document.getElementById('perm-mic-badge');
  const permMicHint           = document.getElementById('perm-mic-hint');
  const permDeniedBox         = document.getElementById('perm-denied-box');
  const permDot               = document.getElementById('perm-dot');
  const permModalActions      = permissionsModal ? permissionsModal.querySelector('.perm-modal-actions') : null;

  const maintenanceMode = window.maintenanceMode || { enabled: false, message: 'The application is currently under maintenance. Please try again later.' };
  // Feature flag: page-render value (server-injected). Updated live before every login UI open.
  let googleSignInEnabled   = window.enableGoogleSignIn !== false;
  let googleSignInAvailable = googleSignInEnabled && Boolean(window.firebaseConfigured);

  /**
   * Fetches the current feature-flag values from Firestore (via server) and
   * updates the local googleSignInEnabled / googleSignInAvailable variables.
   * Returns a Promise so callers can wait for the result before acting.
   */
  function refreshFeatureFlags() {
    return $.getJSON('/api/config/feature-flags')
      .then(function (flags) {
        googleSignInEnabled   = flags.enableGoogleSignIn !== false;
        googleSignInAvailable = googleSignInEnabled && Boolean(window.firebaseConfigured);
        // If the flag flipped to true but Firebase was never initialised, init now.
        if (googleSignInEnabled && !firebaseAuth && window.firebaseConfigured) {
          initFirebase();
        }
      })
      .catch(function () { /* keep the page-render value on any network error */ });
  }

  // ─── Call state helpers ───────────────────────────────────────────────────
  /**
   * Sets a call-state class on <body> so CSS can style the dialer accordingly.
   * States: 'connecting' | 'ringing' | 'active' | null (idle)
   */
  function setCallState(state) {
    document.body.classList.remove('call-connecting', 'call-ringing', 'call-active');
    if (state) document.body.classList.add('call-' + state);
  }

  // ─── WebView detection ───────────────────────────────────────────────────
  function isWebView() {
    var ua = navigator.userAgent || '';
    if (/; wv\)/.test(ua)) return true;
    if (/Android/i.test(ua) && !/Chrome\/\d/.test(ua)) return true;
    if (/iPhone|iPad|iPod/.test(ua) && !/Safari/.test(ua)) return true;
    return false;
  }

  // ─── Auth state ──────────────────────────────────────────────────────────
  /**
   * Toggle the `signed-in` class on <body>.
   * CSS shows/hides .guest-only and .auth-only sections accordingly.
   */
  function setSignedIn(isSignedIn) {
    document.body.classList.toggle('signed-in', isSignedIn);
  }

  // ─── SDK guard ──────────────────────────────────────────────────────────
  if (typeof Twilio === 'undefined' || typeof Twilio.Device === 'undefined') {
    console.error('Twilio Voice SDK v2 did not load.');
    log('Twilio Voice SDK did not load. Check the browser Network tab.');
    setDeviceStatus('SDK Error', 'error');
    return;
  }

  // ─── Boot ────────────────────────────────────────────────────────────────
  setSignedIn(false);          // start in guest state; JS sets signed-in after auth
  setDeviceStatus('Loading', 'loading');
  if (googleSignInEnabled) {
    initFirebase();
  }
  requestLocation();
  wireUiEvents();
  restoreSession();
  initBackgroundCallSupport();

  // ─── Firebase ────────────────────────────────────────────────────────────

  function initFirebase() {
    if (!googleSignInAvailable) {
      console.warn('Firebase client config incomplete – Google Sign-In disabled.');
      return;
    }
    const cfg = window.firebaseConfig;
    try {
      if (!firebase.apps.length) {
        firebase.initializeApp(cfg);
      }
      firebaseAuth = firebase.auth();

      // Handle redirect result from a previous signInWithRedirect call (WebView flow)
      firebaseAuth.getRedirectResult()
        .then(function (result) {
          if (result && result.user) {
            setDeviceStatus('Signing in…', 'loading');
            log('Google redirect sign-in received. Verifying…');
            return result.user.getIdToken().then(handleGoogleAuthSuccess);
          }
        })
        .catch(function (err) {
          if (err && err.code && err.code !== 'auth/popup-blocked-by-browser') {
            console.warn('Redirect sign-in error:', err);
            showToast('Sign-in failed. Please try again.', 'error');
          }
        });
    } catch (err) {
      console.error('Firebase init failed:', err);
    }
  }

  // ─── Cookie helpers ───────────────────────────────────────────────────────

  function setCookie(name, value, days) {
    var expires = new Date(Date.now() + days * 86400000).toUTCString();
    document.cookie = name + '=' + encodeURIComponent(value || '')
      + '; expires=' + expires + '; path=/; SameSite=Lax';
  }

  function getCookie(name) {
    var match = document.cookie.match('(^|;)\\s*' + name + '\\s*=\\s*([^;]+)');
    return match ? decodeURIComponent(match.pop()) : null;
  }

  function saveUserCookies(session) {
    if (!session) return;
    if (session.id)       setCookie(COOKIE_UID,           session.id,       COOKIE_TTL_DAYS);
    if (session.name)     setCookie(COOKIE_NAME,          session.name,     COOKIE_TTL_DAYS);
    if (session.email)    setCookie(COOKIE_EMAIL,         session.email,    COOKIE_TTL_DAYS);
    if (session.identity) setCookie(COOKIE_IDENTITY,      session.identity, COOKIE_TTL_DAYS);
    setCookie(COOKIE_NEEDS_PROFILE, session.needsProfile ? '1' : '0', COOKIE_TTL_DAYS);
  }

  function getUserFromCookies() {
    var uid = getCookie(COOKIE_UID);
    if (!uid) return null;
    return {
      id:           uid,
      name:         getCookie(COOKIE_NAME)  || '',
      email:        getCookie(COOKIE_EMAIL) || '',
      identity:     getCookie(COOKIE_IDENTITY) || '',
      needsProfile: getCookie(COOKIE_NEEDS_PROFILE) === '1'
    };
  }

  function clearUserCookies() {
    [COOKIE_UID, COOKIE_NAME, COOKIE_EMAIL, COOKIE_IDENTITY, COOKIE_NEEDS_PROFILE].forEach(function (n) {
      document.cookie = n + '=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/';
    });
  }

  // ─── Toast system ─────────────────────────────────────────────────────────

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

  // ─── Button spinner helpers ───────────────────────────────────────────────

  function setBusy(btn, busy, originalText) {
    if (!btn) return;
    if (busy) {
      btn.disabled = true;
      if (originalText !== undefined) btn.dataset.origText = btn.textContent;
      var spinner = document.createElement('span');
      spinner.className = 'spinner';
      btn.prepend(spinner);
    } else {
      btn.disabled = false;
      var s = btn.querySelector('.spinner');
      if (s) s.remove();
      if (btn.dataset.origText) {
        btn.textContent = btn.dataset.origText;
        delete btn.dataset.origText;
      }
    }
  }

  // ─── UI wiring ───────────────────────────────────────────────────────────

  function wireUiEvents() {
    // Firebase setup notice
    if (btnCloseSetupNotice) {
      btnCloseSetupNotice.addEventListener('click', function () {
        if (firebaseSetupNotice) firebaseSetupNotice.style.display = 'none';
      });
    }

    // Login button (guest panel) – always resolves the live flag first
    var btnLogin = document.getElementById('btn-login');
    if (btnLogin) {
      btnLogin.addEventListener('click', function () {
        refreshFeatureFlags().then(function () {
          if (googleSignInEnabled) { showGoogleAuthModal(); } else { showSimpleLoginModal(); }
        });
      });
    }

    // Google Sign-In modal (only wired when Google Sign-In is enabled)
    if (googleSignInEnabled) {
      if (btnGoogleSignin) btnGoogleSignin.addEventListener('click', signInWithGoogle);
      if (btnCloseAuth) {
        btnCloseAuth.addEventListener('click', function () {
          closeGoogleAuthModal();
          pendingDestination = '';
        });
      }
      if (googleAuthModal) {
        googleAuthModal.addEventListener('click', function (e) {
          if (e.target === googleAuthModal) {
            closeGoogleAuthModal();
            pendingDestination = '';
          }
        });
      }
    }

    // Simple login modal — wired always; guards in showSimpleLoginModal() prevent misuse
    if (simpleLoginForm) simpleLoginForm.addEventListener('submit', submitSimpleLogin);
    if (btnCloseSimpleLogin) {
      btnCloseSimpleLogin.addEventListener('click', function () {
        closeSimpleLoginModal();
        pendingDestination = '';
      });
    }
    if (simpleLoginModal) {
      simpleLoginModal.addEventListener('click', function (e) {
        if (e.target === simpleLoginModal) {
          closeSimpleLoginModal();
          pendingDestination = '';
        }
      });
    }

    // Profile form
    profileForm.addEventListener('submit', submitProfile);

    // Terms modal
    closeTerms.addEventListener('click', function () { termsModal.classList.remove('open'); });
    acceptTerms.addEventListener('click', function () {
      if (profileTerms) profileTerms.checked = true;
      termsModal.classList.remove('open');
    });
    termsModal.addEventListener('click', function (e) {
      if (e.target === termsModal) termsModal.classList.remove('open');
    });

    // Wallet / logout
    topUpButton.addEventListener('click', topUpWallet);
    logoutButton.addEventListener('click', logout);

    // Call buttons
    document.getElementById('button-call').onclick = makeCall;
    document.getElementById('button-hangup').onclick = function () {
      log('Hanging up…');
      if (activeCall) { activeCall.disconnect(); return; }
      if (device)     { device.disconnectAll(); }
    };

    // Audio devices
    document.getElementById('get-devices').onclick = function () {
      navigator.mediaDevices.getUserMedia({ audio: true })
        .then(updateAllDevices)
        .catch(function (error) {
          console.error('Microphone access denied:', error);
          log('Unable to access microphone: ' + error.message);
        });
    };

    // Clear button – delete only last digit (single tap), long press clears all
    let clearPressTimer = null;
    clearButton.addEventListener('mousedown', function () {
      clearPressTimer = setTimeout(function () {
        phoneNumberInput.value = '';
        showToast('Number cleared', 'info', 1500);
      }, 800);
    });
    clearButton.addEventListener('mouseup', function () {
      if (clearPressTimer) {
        clearTimeout(clearPressTimer);
        clearPressTimer = null;
        // Single click: delete only last digit
        phoneNumberInput.value = phoneNumberInput.value.slice(0, -1);
      }
    });
    clearButton.addEventListener('mouseleave', function () {
      if (clearPressTimer) {
        clearTimeout(clearPressTimer);
        clearPressTimer = null;
      }
    });
    // Touch support for mobile
    clearButton.addEventListener('touchstart', function (e) {
      e.preventDefault();
      clearPressTimer = setTimeout(function () {
        phoneNumberInput.value = '';
        showToast('Number cleared', 'info', 1500);
      }, 800);
    });
    clearButton.addEventListener('touchend', function (e) {
      e.preventDefault();
      if (clearPressTimer) {
        clearTimeout(clearPressTimer);
        clearPressTimer = null;
        phoneNumberInput.value = phoneNumberInput.value.slice(0, -1);
      }
    });

    // Dialpad – prevent any native keyboard from appearing
    dialpad.addEventListener('click', function (event) {
      const key = event.target.closest('.dial-key');
      if (!key) return;
      handleDialKey(key.getAttribute('data-key'));
    });
    // Block native keyboard on phone input (inputmode="none" + readonly covers most cases,
    // but we also preventDefault on focus for extra safety on older Android WebViews)
    phoneNumberInput.addEventListener('focus', function (e) { e.preventDefault(); });
    phoneNumberInput.addEventListener('keydown', function (e) {
      // Allow only Backspace for accessibility on desktop
      if (e.key === 'Backspace') {
        phoneNumberInput.value = phoneNumberInput.value.slice(0, -1);
        formatPhoneNumber();
        e.preventDefault();
        return;
      }
      // Allow Ctrl+V / Cmd+V for paste
      if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
        return; // Let paste handler deal with it
      }
      e.preventDefault();
    });

    // Paste support - handle various phone number formats
    phoneNumberInput.addEventListener('paste', function (e) {
      e.preventDefault();
      const pastedText = (e.clipboardData || window.clipboardData).getData('text');
      const sanitized = sanitizePhoneNumber(pastedText);
      if (sanitized) {
        phoneNumberInput.value = sanitized;
        formatPhoneNumber();
        showToast('Number pasted: ' + formatPhoneDisplay(sanitized), 'success', 2000);
      } else {
        showToast('Invalid phone number format', 'warning');
      }
    });

    // Enable right-click paste (context menu)
    phoneNumberInput.addEventListener('contextmenu', function (e) {
      // Allow context menu for paste functionality
    });

    // Speaker / ringtone
    speakerDevices.addEventListener('change', function () {
      if (!device || !device.audio) return;
      const ids = Array.from(speakerDevices.selectedOptions).map(function (o) { return o.getAttribute('data-id'); });
      device.audio.speakerDevices.set(ids);
    });
    ringtoneDevices.addEventListener('change', function () {
      if (!device || !device.audio) return;
      const ids = Array.from(ringtoneDevices.selectedOptions).map(function (o) { return o.getAttribute('data-id'); });
      device.audio.ringtoneDevices.set(ids);
    });

    // Recharge modal
    if (rechargeForm) {
      rechargeForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var amount = Number(rechargeAmountEl ? rechargeAmountEl.value : 0);
        if (!Number.isFinite(amount) || amount <= 0) {
          showToast('Please enter a valid amount.', 'warning');
          return;
        }
        var gateway = 'razorpay';//rechargeGatewayEl && rechargeGatewayEl.value ? rechargeGatewayEl.value : 'razorpay';
        closeRechargeModal();
        startPaymentOrder(amount, gateway);
      });
    }
    if (btnRechargeCancel) {
      btnRechargeCancel.addEventListener('click', closeRechargeModal);
    }
    if (rechargeModal) {
      rechargeModal.addEventListener('click', function (e) {
        if (e.target === rechargeModal) closeRechargeModal();
      });
    }

    // Permissions modal
    if (btnCheckPermissions)  btnCheckPermissions.addEventListener('click', openPermissionsModal);
    if (btnRequestMic)        btnRequestMic.addEventListener('click', requestMicPermission);
    if (btnClosePermissions)  btnClosePermissions.addEventListener('click', closePermissionsModal);
    if (btnClosePermissionsX) btnClosePermissionsX.addEventListener('click', closePermissionsModal);
    if (permissionsModal) {
      permissionsModal.addEventListener('click', function (e) {
        if (e.target === permissionsModal) closePermissionsModal();
      });
    }
  }

  function closeRechargeModal() {
    if (rechargeModal) rechargeModal.classList.remove('open');
  }

  // ─── Session restore ─────────────────────────────────────────────────────

  function restoreSession() {
    $.getJSON('/user/me')
      .then(function (session) {
        currentUser = session;
        saveUserCookies(session);
        if (session.needsProfile) {
          showProfileModal();
          setDeviceStatus('Complete Profile', 'loading');
          log('Welcome back ' + (session.name || 'there') + '! Please complete your profile.');
          return;
        }
        return $.getJSON('/user/token').then(function (tokenData) {
          startLoggedInUi(session, tokenData.token);
        });
      })
      .catch(function () {
        // 401 – server session expired or absent
        setSignedIn(false);
        var cookieUser = getUserFromCookies();
        if (googleSignInEnabled && cookieUser && cookieUser.id && googleSignInAvailable && firebaseAuth) {
          trySilentReauth(cookieUser);
        } else if (cookieUser && cookieUser.name) {
          setDeviceStatus('Ready to Call', 'offline');
          var hint = googleSignInEnabled
            ? 'Welcome back, ' + cookieUser.name + '! Enter a number and tap Call to sign in.'
            : 'Welcome back, ' + cookieUser.name + '! Enter a number and tap Call to continue.';
          log(hint);
        } else {
          setDeviceStatus('Ready to Call', 'offline');
          var prompt = googleSignInEnabled
            ? 'Enter a number and tap Call. You will be prompted to sign in with Google.'
            : 'Enter a number and tap Call. You will be asked for your mobile number.';
          log(prompt);
        }
      });
  }

  function trySilentReauth(cookieUser) {
    var displayName = cookieUser.name || cookieUser.email || 'you';
    setDeviceStatus('Restoring session', 'loading');
    log('Welcome back, ' + displayName + '! Restoring your session…');
    var unsubscribe = firebaseAuth.onAuthStateChanged(function (firebaseUser) {
      unsubscribe();
      if (firebaseUser) {
        firebaseUser.getIdToken(false)
          .then(function (idToken) { return handleGoogleAuthSuccess(idToken); })
          .catch(function (err) {
            console.warn('Silent reauth error:', err);
            fallbackToSignIn(cookieUser);
          });
      } else {
        fallbackToSignIn(cookieUser);
      }
    });
  }

  function fallbackToSignIn(cookieUser) {
    setSignedIn(false);
    setDeviceStatus('Ready to Call', 'offline');
    var name = cookieUser && cookieUser.name ? cookieUser.name : null;
    log(name ? 'Hi ' + name + '! Tap Call to sign in again.' : 'Enter a number and tap Call to sign in.');
  }

  // ─── Logged-in UI ────────────────────────────────────────────────────────

  function startLoggedInUi(session, token) {
    currentUser = session;
    saveUserCookies(session);
    setSignedIn(true);                                               // show auth panels, hide guest panel
    updateWalletFromSession(session);
    setClientNameUI((session.name || session.email || 'User') + ' (' + (session.identity || '…') + ')');
    initializeDevice(token, session.identity);
    refreshWallet();
    refreshHistory();
    closeGoogleAuthModal();
    closeSimpleLoginModal();
    closeProfileModal();
    log('Signed in as ' + (session.name || session.email || session.number) + '. Wallet: ₹' + money(session.walletBalance) + '.');
    showToast('Signed in as ' + escapeHtml(session.name || session.email || session.number), 'success');
  }

  // ─── Logout ──────────────────────────────────────────────────────────────

  function logout() {
    if (activeCall) { activeCall.disconnect(); activeCall = null; }
    if (device)     { device.destroy();        device     = null; }
    callRingTime   = null;
    callAnswerTime = null;
    setCallState(null);
    stopCallTimer();

    $.post('/user/logout').always(function () {
      if (firebaseAuth) firebaseAuth.signOut().catch(function () {});
      clearUserCookies();
      currentUser = null;
      wallet      = null;
      setSignedIn(false);                                            // show guest panel, hide auth panels
      document.getElementById('output-selection').style.display = 'none';
      resetCallUi();
      updateWallet({ walletBalance: 0, ratePerMinute: 10, minimumBalance: 1, canCall: false });
      historyList.innerHTML = '<p class="empty-state">Sign in to view call history.</p>';
      setClientNameUI(googleSignInEnabled ? 'Sign in with Google to start calling.' : 'Enter your number to start calling.');
      setDeviceStatus('Ready to Call', 'offline');
      log('Logged out successfully.');
      showToast('Logged out.', 'info');
    });
  }

  // ─── Google Sign-In ──────────────────────────────────────────────────────

  function showGoogleAuthModal() {
    if (!googleSignInEnabled) {
      // Feature flag disabled – use simple login instead
      showSimpleLoginModal();
      return;
    }
    if (!googleSignInAvailable) {
      if (firebaseSetupNotice) firebaseSetupNotice.style.display = 'flex';
      log('Google Sign-In is not configured. See the setup notice for instructions.');
      return;
    }
    if (googleAuthModal) googleAuthModal.classList.add('open');
  }

  function closeGoogleAuthModal() {
    if (googleAuthModal) googleAuthModal.classList.remove('open');
    if (btnGoogleSignin) setBusy(btnGoogleSignin, false);
  }

  // ─── Simple login modal ───────────────────────────────────────────────────

  function showSimpleLoginModal() {
    if (simpleLoginModal) {
      simpleLoginModal.classList.add('open');
      requestLocation();   // refresh geolocation into hidden field
      setTimeout(function () { if (simpleLoginNumber) simpleLoginNumber.focus(); }, 150);
    }
  }

  function closeSimpleLoginModal() {
    if (simpleLoginModal) simpleLoginModal.classList.remove('open');
    var btn = document.getElementById('btn-simple-login-submit');
    if (btn) setBusy(btn, false);
  }

  function submitSimpleLogin(event) {
    event.preventDefault();
    var number = simpleLoginNumber ? simpleLoginNumber.value.trim() : '';
    if (!number) {
      showToast('Please enter your mobile number.', 'warning');
      if (simpleLoginNumber) simpleLoginNumber.focus();
      return;
    }

    var location = (simpleLoginLocationEl && simpleLoginLocationEl.value.trim())
      ? simpleLoginLocationEl.value.trim()
      : '';   // server defaults to "x" when blank

    var payload = { userNumber: number, location: location };
    var submitBtn = document.getElementById('btn-simple-login-submit');
    setBusy(submitBtn, true);
    log('Signing in…');

    $.ajax({ url: '/user/simple-login', method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
      .then(function (session) {
        currentUser = session;
        var token = session.token;
        var afterLogin;
        if (token) {
          afterLogin = Promise.resolve(startLoggedInUi(session, token));
        } else {
          afterLogin = $.getJSON('/user/token').then(function (td) { startLoggedInUi(session, td.token); });
        }
        return afterLogin.then(function () { resumePendingCall(); });
      })
      .catch(function (error) {
        console.error('Simple login failed:', error);
        var msg = readError(error, 'Sign-in failed. Please try again.');
        log(msg);
        showToast(msg, 'error');
        setDeviceStatus('Sign-in Error', 'error');
      })
      .always(function () { setBusy(submitBtn, false); });
  }

  function signInWithGoogle() {
    if (!googleSignInAvailable || !firebaseAuth) {
      log('Google Sign-In is not configured. Please contact the administrator.');
      showToast('Google Sign-In is not configured.', 'error');
      return;
    }

    const provider = new firebase.auth.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    provider.addScope('email');
    provider.addScope('profile');

    setBusy(btnGoogleSignin, true);
    log('Opening Google Sign-In…');

    // Use redirect for WebView environments (popup does not work in WebView)
    if (isWebView()) {
      log('WebView detected – using redirect sign-in…');
      firebaseAuth.signInWithRedirect(provider)
        .catch(function (err) {
          console.error('Redirect sign-in failed:', err);
          showToast('Sign-in failed. Please try again.', 'error');
          log('Sign-in failed: ' + (err.message || 'Unknown error'));
          setBusy(btnGoogleSignin, false);
        });
      return; // page will reload after redirect completes
    }

    // Desktop / standard browser – use popup with redirect fallback
    firebaseAuth.signInWithPopup(provider)
      .then(function (result) { return result.user.getIdToken(); })
      .then(function (idToken) { return handleGoogleAuthSuccess(idToken); })
      .catch(function (error) {
        console.error('Google Sign-In failed:', error);
        if (error.code === 'auth/popup-blocked' || error.code === 'auth/operation-not-supported-in-this-environment') {
          // Popup was blocked – fall back to redirect
          log('Popup blocked. Trying redirect sign-in…');
          return firebaseAuth.signInWithRedirect(provider);
        }
        if (error.code !== 'auth/popup-closed-by-user' && error.code !== 'auth/cancelled-popup-request') {
          showToast('Sign-in failed. Please try again.', 'error');
          log('Google Sign-In failed: ' + (error.message || 'Unknown error'));
        }
      })
      .finally(function () { setBusy(btnGoogleSignin, false); });
  }

  function handleGoogleAuthSuccess(idToken) {
    log('Verifying with server…');
    return $.ajax({
      url: '/user/google-login',
      method: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ idToken: idToken })
    })
      .then(function (session) {
        currentUser = session;
        saveUserCookies(session);
        if (session.needsProfile) {
          closeGoogleAuthModal();
          showProfileModal();
          setDeviceStatus('Complete Profile', 'loading');
          log('Welcome ' + (session.name || session.email) + '! Please enter your mobile number to continue.');
        } else {
          var token = session.token;
          var afterLogin;
          if (token) {
            afterLogin = Promise.resolve(startLoggedInUi(session, token));
          } else {
            afterLogin = $.getJSON('/user/token').then(function (td) { startLoggedInUi(session, td.token); });
          }
          return afterLogin.then(function () { resumePendingCall(); });
        }
      })
      .catch(function (error) {
        console.error('Server auth failed:', error);
        var msg = readError(error, 'Sign-in failed. Please try again.');
        log(msg);
        showToast(msg, 'error');
        setDeviceStatus('Sign-in Error', 'error');
      });
  }

  // ─── Profile form ─────────────────────────────────────────────────────────

  function showProfileModal() {
    if (profileModal) profileModal.classList.add('open');
    requestLocation();
  }

  function closeProfileModal() {
    if (profileModal) profileModal.classList.remove('open');
  }

  function submitProfile(event) {
    event.preventDefault();
    const phone = profilePhone ? profilePhone.value.trim() : '';
    const terms = profileTerms ? profileTerms.checked : false;

    if (!phone) {
      showToast('Please enter your mobile number.', 'warning');
      if (profilePhone) profilePhone.focus();
      return;
    }
    if (!terms) {
      showToast('Please accept the Terms and Conditions to continue.', 'warning');
      termsModal.classList.add('open');
      return;
    }

    const payload = {
      phoneNumber: phone,
      location: profileLocation ? (profileLocation.value || '') : '',   // '' → server stores "x"
      termsAccepted: true
    };

    const submitBtn = profileForm.querySelector('button[type="submit"]');
    setBusy(submitBtn, true);
    log('Saving profile…');

    $.ajax({ url: '/user/profile', method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
      .then(function (session) {
        currentUser = session;
        var token = session.token;
        if (token) {
          startLoggedInUi(session, token);
        } else {
          return $.getJSON('/user/token').then(function (td) { startLoggedInUi(session, td.token); });
        }
        resumePendingCall();
      })
      .catch(function (error) {
        console.error('Profile save failed:', error);
        var msg = readError(error, 'Failed to save profile. Please try again.');
        log(msg);
        showToast(msg, 'error');
      })
      .always(function () { setBusy(submitBtn, false); });
  }

  // ─── Resume pending call ──────────────────────────────────────────────────

  function resumePendingCall() {
    if (pendingDestination) {
      var dest = pendingDestination;
      pendingDestination = '';
      phoneNumberInput.value = dest;
      log('Resuming call to ' + dest + '…');
      setTimeout(makeCall, 900);
    }
  }

  // ─── Device ──────────────────────────────────────────────────────────────

  function initializeDevice(token, identity) {
    if (device) { device.destroy(); }
    device = new Twilio.Device(token, { logLevel: 1 });
    registerDeviceEvents(identity);
    setDeviceStatus('Registering', 'calling');
    device.register();
  }

  // ─── Make call ───────────────────────────────────────────────────────────

  function makeCall() {
    const to = phoneNumberInput.value.trim();

    if (maintenanceMode.enabled) {
      const message = maintenanceMode.message || 'The application is currently under maintenance.';
      log(message);
      setDeviceStatus('Maintenance', 'error');
      applyMaintenanceModeUi();
      return;
    }

    if (!to) {
      showToast('Please enter a phone number or client name.', 'warning');
      log('Please enter a phone number or client name.');
      return;
    }

    // Validate phone number format
    if (!isValidPhoneNumber(to)) {
      showToast('Please enter a valid phone number (at least 10 digits).', 'warning');
      log('Invalid phone number format. Please enter at least 10 digits.');
      return;
    }

    if (!currentUser) {
      pendingDestination = to;
      refreshFeatureFlags().then(function () {
        if (googleSignInEnabled) { showGoogleAuthModal(); } else { showSimpleLoginModal(); }
      });
      return;
    }

    if (currentUser.needsProfile) {
      pendingDestination = to;
      showProfileModal();
      return;
    }

    if (!device) {
      showToast('Twilio Device is not ready yet. Please wait…', 'warning');
      log('Twilio Device is not ready yet. Please wait…');
      return;
    }

    // ── Mobile-safe microphone check ─────────────────────────────────────────
    // iOS Safari and some Android browsers lose the user-gesture context after
    // asynchronous AJAX calls.  The ONLY reliable way to ensure getUserMedia
    // succeeds on ALL mobile browsers is to call it HERE — as the very first
    // async operation, directly from this tap-event context — BEFORE any
    // network requests (maintenance check, wallet refresh, etc.).
    //
    //  • Android Chrome / Firefox: if permission is already 'granted' this
    //    resolves instantly with no prompt.
    //  • iOS Safari (no Permissions API): shows the system prompt on first use;
    //    resolves instantly on subsequent uses within the same session.
    //  • If mic is denied: we catch the error, show the permissions modal and
    //    abort the call.
    //
    // The pre-warm stream is stopped immediately after we confirm access so it
    // does not conflict when the Twilio SDK opens its own audio track.

    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      navigator.mediaDevices.getUserMedia({ audio: true })
        .then(function (preWarmStream) {
          preWarmStream.getTracks().forEach(function (t) { t.stop(); });
          updatePermDot('granted');
          _proceedWithCall(to);
        })
        .catch(function (err) {
          console.warn('Microphone access failed in makeCall:', err);
          var denied = err.name === 'NotAllowedError'
                    || err.name === 'PermissionDeniedError'
                    || err.name === 'SecurityError';
          if (denied) {
            log('Microphone access is blocked. Please allow it using the 🎤 button.');
            showToast('Microphone is blocked. Tap 🎤 to fix, then call again.', 'error', 7000);
            setDeviceStatus('Mic Blocked', 'error');
            updatePermDot('denied');
          } else {
            log('Could not access microphone: ' + (err.message || err.name) + '. Tap 🎤 for help.');
            showToast('Microphone error. Tap 🎤 for help.', 'error', 7000);
          }
          openPermissionsModal();
        });
    } else {
      // No getUserMedia support (very old browser) – attempt the call anyway.
      _proceedWithCall(to);
    }
  }

  /**
   * _proceedWithCall – runs the maintenance → wallet → device.connect() chain.
   * Called only after the microphone pre-warm in makeCall() has confirmed
   * (or could not check) microphone access, so Twilio's internal getUserMedia
   * will succeed without needing another user gesture.
   */
  function _proceedWithCall(to) {
    refreshMaintenanceMode()
      .then(function (status) {
        if (status.enabled) { applyMaintenanceModeUi(); return null; }
        return refreshWallet();
      })
      .then(function (walletData) {
        if (!walletData) return;
        if (!walletData.canCall) {
          var msg = 'Wallet balance is too low. Minimum required is ₹' + money(walletData.minimumBalance) + '.';
          log(msg);
          showToast(msg, 'error');
          setDeviceStatus('Low Wallet', 'error');
          return;
        }

        currentDestination = to;
        callRingTime   = null;
        callAnswerTime = null;
        log('Calling ' + to + '…');
        setDeviceStatus('Calling', 'calling');
        setCallState('connecting');

        return Promise.resolve(device.connect({ params: { DialedNumber: to } }))
          .then(function (call) {
            activeCall = call;
            setupCallHandlers(call);
          });
      })
      .catch(function (error) {
        console.error('Unable to connect call:', error);
        var msg = readError(error, 'Unable to connect call.');
        log(msg);
        showToast(msg, 'error');
        setDeviceStatus('Ready', 'ready');
        resetCallUi();
      });
  }

  // ─── Microphone permission helpers ───────────────────────────────────────

  /**
   * getMicState – resolves to 'granted' | 'denied' | 'prompt' | 'unknown'.
   * Uses the Permissions API where available; never triggers a prompt.
   */
  function getMicState() {
    if (navigator.permissions && navigator.permissions.query) {
      return navigator.permissions.query({ name: 'microphone' })
        .then(function (result) { return result.state; })
        .catch(function () { return 'unknown'; });
    }
    return Promise.resolve('unknown');
  }

  /** updatePermDot – colours the indicator dot on the 🎤 button. */
  function updatePermDot(state) {
    if (!permDot) return;
    permDot.className = 'perm-dot' +
      (state === 'granted' ? ' perm-dot-granted' :
       state === 'denied'  ? ' perm-dot-denied'  :
       state === 'prompt'  ? ' perm-dot-prompt'  : '');
  }

  // ─── Permissions modal ────────────────────────────────────────────────────

  function openPermissionsModal() {
    if (permissionsModal) {
      permissionsModal.classList.add('open');
      refreshPermissionUI();
    }
  }

  function closePermissionsModal() {
    if (permissionsModal) permissionsModal.classList.remove('open');
  }

  function refreshPermissionUI() {
    if (permMicBadge) { permMicBadge.className = 'perm-badge perm-badge-checking'; permMicBadge.textContent = 'Checking…'; }
    if (permMicHint)  permMicHint.style.display  = 'none';
    if (permDeniedBox) permDeniedBox.style.display = 'none';
    if (permModalActions) {
      permModalActions.classList.remove('mic-granted');
      if (btnRequestMic) { btnRequestMic.disabled = false; btnRequestMic.textContent = 'Allow Microphone'; }
    }
    getMicState().then(function (state) {
      updatePermDot(state);
      if (state === 'granted') {
        if (permMicBadge)  { permMicBadge.className = 'perm-badge perm-badge-granted'; permMicBadge.textContent = 'Granted ✓'; }
        if (permMicHint)   { permMicHint.textContent = 'Microphone access is allowed. You can make calls.'; permMicHint.style.display = 'block'; }
        if (permModalActions) permModalActions.classList.add('mic-granted');
      } else if (state === 'denied') {
        if (permMicBadge)  { permMicBadge.className = 'perm-badge perm-badge-denied'; permMicBadge.textContent = 'Blocked ✕'; }
        if (permMicHint)   { permMicHint.textContent = 'Microphone access is blocked by your browser. Follow the steps below to unblock it.'; permMicHint.style.display = 'block'; }
        if (permDeniedBox)  permDeniedBox.style.display = 'block';
        if (btnRequestMic)  btnRequestMic.disabled = true;
      } else if (state === 'prompt') {
        if (permMicBadge)  { permMicBadge.className = 'perm-badge perm-badge-prompt'; permMicBadge.textContent = 'Not Granted'; }
        if (permMicHint)   { permMicHint.textContent = 'Tap "Allow Microphone" below. Your browser will ask for permission — tap Allow.'; permMicHint.style.display = 'block'; }
      } else {
        // 'unknown' – Permissions API not available (iOS Safari, some Android browsers).
        // We cannot tell the state without actually calling getUserMedia.
        if (permMicBadge) { permMicBadge.className = 'perm-badge perm-badge-prompt'; permMicBadge.textContent = 'Tap to Check'; }
        if (permMicHint)  {
          permMicHint.textContent = 'Tap "Allow Microphone" to grant access. '
            + 'Your browser will ask permission — tap Allow. '
            + '(iOS Safari does not report permission status without asking.)';
          permMicHint.style.display = 'block';
        }
      }
    });
  }

  /**
   * requestMicPermission – called DIRECTLY from the "Allow Microphone" button
   * click event, preserving the user-gesture context required by mobile browsers
   * (Android Chrome, iOS Safari) for getUserMedia to succeed.
   */
  function requestMicPermission() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showToast('Your browser does not support microphone access.', 'error');
      return;
    }
    if (btnRequestMic) { btnRequestMic.disabled = true; btnRequestMic.textContent = 'Requesting…'; }
    navigator.mediaDevices.getUserMedia({ audio: true })
      .then(function (stream) {
        stream.getTracks().forEach(function (track) { track.stop(); });
        log('Microphone access granted. You can now make calls.');
        showToast('Microphone access granted! You can now make calls.', 'success');
        refreshPermissionUI();
        updatePermDot('granted');
        if (btnRequestMic) { btnRequestMic.disabled = false; btnRequestMic.textContent = 'Allow Microphone'; }
      })
      .catch(function (err) {
        console.warn('getUserMedia failed:', err);
        var denied = err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError';
        var msg = denied
          ? 'Microphone access was denied. Please allow it in your browser settings.'
          : 'Could not access microphone: ' + (err.message || err.name);
        log(msg);
        showToast(msg, 'error', 7000);
        refreshPermissionUI();
        if (btnRequestMic) { btnRequestMic.disabled = false; btnRequestMic.textContent = 'Allow Microphone'; }
      });
  }

  // ─── Device events ───────────────────────────────────────────────────────

  function registerDeviceEvents(identity) {
    device.on('registered', function () {
      log('Twilio.Device Ready!');
      setDeviceStatus('Ready', 'ready');
      document.getElementById('output-selection').style.display = 'none';
      setClientNameUI(currentUser.name + ' (' + identity + ')');
      setupAudioDeviceSelection();
      applyMaintenanceModeUi();
      // Passively check mic permission state and update the status dot.
      // We do NOT call getUserMedia here: this callback runs outside any
      // user-gesture context on mobile (async after device.register()), so
      // getUserMedia would silently fail or be blocked on Android/iOS Chrome.
      // The user grants permission via the dedicated 🎤 button instead.
      getMicState().then(function (state) {
        updatePermDot(state);
        if (state === 'denied') {
          log('⚠ Microphone access is blocked. Tap the 🎤 button to fix it before calling.');
          showToast('Microphone is blocked. Tap 🎤 to fix before calling.', 'warning', 7000);
        } else if (state === 'prompt') {
          log('Microphone permission not yet granted. Tap the 🎤 button to allow it.');
          showToast('Tap the 🎤 button to grant microphone access.', 'info', 5000);
        }
      });
    });

    device.on('unregistered', function () {
      log('Twilio.Device Offline.');
      setDeviceStatus('Offline', 'offline');
    });

    device.on('error', function (error) {
      console.error('Twilio Device Error:', error);
      log('Twilio.Device Error: ' + error.message);
      setDeviceStatus('Error', 'error');
      showToast('Device error: ' + error.message, 'error');
    });

    device.on('incoming', function (call) {
      log('Incoming call from ' + call.parameters.From);
      setDeviceStatus('Incoming', 'calling');
      activeCall = call;
      currentDestination = call.parameters.From || 'incoming';
      setupCallHandlers(call);
      call.accept();
    });

    device.on('tokenWillExpire', refreshToken);
  }

  // ─── Call handlers ───────────────────────────────────────────────────────

  function setupCallHandlers(call) {
    call.on('ringing', function () {
      if (!callRingTime) {
        callRingTime = Date.now();     // total duration starts from ring
        startCallTimer();              // timer shows elapsed from ring start
      }
      setDeviceStatus('Ringing', 'calling');
      setCallState('ringing');
      log('Ringing…');
    });

    call.on('accept', function () {
      activeCall = call;
      callAnswerTime = Date.now();   // BILLING starts from answered time
      if (!callRingTime) callRingTime = callAnswerTime; // fallback if ringing event missed
      const rate = wallet && wallet.ratePerMinute ? wallet.ratePerMinute : 10;
      log('Call answered! Billing starts now at ₹' + money(rate) + '/min.');
      setDeviceStatus('In Call', 'calling');
      setCallState('active');
      document.getElementById('button-call').style.display   = 'none';
      document.getElementById('button-hangup').style.display = 'inline';
      volumeIndicators.style.display = 'block';
      requestWakeLock();   // prevent screen sleep during active call
    });

    call.on('disconnect', function () {
      log('Call ended.');
      chargeForCall(call, 'completed');
      activeCall = null;
      setDeviceStatus('Ready', 'ready');
      setCallState(null);
      resetCallUi();
    });

    call.on('cancel', function () {
      log('Call cancelled.');
      chargeForCall(call, 'cancelled');
      activeCall = null;
      setDeviceStatus('Ready', 'ready');
      setCallState(null);
      resetCallUi();
    });

    call.on('reject', function () {
      log('Call rejected.');
      chargeForCall(call, 'rejected');
      activeCall = null;
      setDeviceStatus('Ready', 'ready');
      setCallState(null);
      resetCallUi();
    });

    call.on('error', function (error) {
      console.error('Call Error:', error);
      // Error 31402 = AcquisitionFailedError – microphone could not be opened.
      var isAcqError = error && (
        error.code === 31402 ||
        (error.name    && error.name.indexOf('AcquisitionFailed')    !== -1) ||
        (error.message && error.message.indexOf('AcquisitionFailed') !== -1)
      );
      if (isAcqError) {
        log('Microphone could not be opened (error 31402). Please grant microphone permission and try calling again.');
        showToast('Microphone access failed. Tap 🎤 to grant permission, then call again.', 'error', 8000);
        openPermissionsModal();
      } else {
        log('Call Error: ' + error.message);
        showToast('Call error: ' + error.message, 'error');
      }
      chargeForCall(call, 'error');
      activeCall = null;
      setDeviceStatus('Ready', 'ready');
      setCallState(null);
      resetCallUi();
    });

    call.on('volume', function (inputVolume, outputVolume) {
      updateVolumeBar(inputVolumeBar, inputVolume);
      updateVolumeBar(outputVolumeBar, outputVolume);
    });
  }

  // ─── Call timer ───────────────────────────────────────────────────────────

  function startCallTimer() {
    stopCallTimer();
    if (!callTimerEl) return;
    callTimerEl.classList.add('active');
    callTimerInterval = setInterval(function () {
      var ref = callRingTime || callAnswerTime;
      if (!ref) { stopCallTimer(); return; }
      var elapsed = Math.floor((Date.now() - ref) / 1000);
      var mins = Math.floor(elapsed / 60);
      var secs = elapsed % 60;
      callTimerEl.textContent = mins + ':' + (secs < 10 ? '0' : '') + secs;
    }, 1000);
  }

  function stopCallTimer() {
    if (callTimerInterval) { clearInterval(callTimerInterval); callTimerInterval = null; }
    if (callTimerEl) { callTimerEl.classList.remove('active'); callTimerEl.textContent = '0:00'; }
  }

  // ─── Charging ────────────────────────────────────────────────────────────

  /**
   * Charge the user for a completed call.
   *
   * Billing starts from when the call was ANSWERED (callAnswerTime), not from ringing.
   * If the call was never answered (busy/no-answer/rejected), connectedDurationSeconds = 0
   * and the server skips billing.
   */
  function chargeForCall(call, status) {
    // Nothing to charge if we never even started ringing
    if (!callRingTime && !callAnswerTime) return;

    var endTimeMs              = Date.now();
    var ringStartMs            = callRingTime  || callAnswerTime;
    var answerMs               = callAnswerTime;   // null = never answered

    var totalDurationSeconds     = Math.max(0, Math.ceil((endTimeMs - ringStartMs) / 1000));
    var connectedDurationSeconds = answerMs ? Math.max(0, Math.ceil((endTimeMs - answerMs) / 1000)) : 0;
    var startIso = new Date(ringStartMs).toISOString();
    var endIso   = new Date(endTimeMs).toISOString();

    callRingTime   = null;
    callAnswerTime = null;
    stopCallTimer();

    if (connectedDurationSeconds === 0) {
      // Call was not answered – no billing
      log('Call ' + status + '. No charge (not answered).');
      showToast('Call ' + status + '. No charge.', 'info', 3000);
      return;
    }

    $.ajax({
      url: '/call/charge',
      method: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({
        destination:              currentDestination,
        callSid:                  call && call.parameters ? call.parameters.CallSid : null,
        durationSeconds:          Math.max(1, totalDurationSeconds),      // ring → hangup
        connectedDurationSeconds: connectedDurationSeconds,               // answer → hangup (billing)
        status:                   status,
        startTime:                startIso,
        endTime:                  endIso,
        disconnectReason:         status
      })
    })
      .then(function (walletData) {
        updateWallet(walletData);
        refreshHistory();
        var charged = Math.max(0, (wallet ? wallet.walletBalance : 0) - walletData.walletBalance);
        log('Call billed for ' + connectedDurationSeconds + 's connected. Wallet: ₹' + money(walletData.walletBalance));
        showToast('Call ended · ' + connectedDurationSeconds + 's connected · Wallet ₹' + money(walletData.walletBalance), 'info');
      })
      .catch(function (error) {
        console.error('Unable to charge call:', error);
        log(readError(error, 'Unable to update wallet charge.'));
      });
  }

  // ─── Token refresh ───────────────────────────────────────────────────────

  function refreshToken() {
    $.getJSON('/user/token')
      .then(function (data) {
        device.updateToken(data.token);
        log('Access token refreshed.');
      })
      .catch(function (error) {
        console.error('Unable to refresh token:', error);
        log('Unable to refresh access token. Please re-login if calls fail.');
      });
  }

  // ─── Wallet ──────────────────────────────────────────────────────────────

  function refreshWallet() {
    return $.getJSON('/user/wallet').then(function (walletData) {
      updateWallet(walletData);
      return walletData;
    });
  }

  function refreshMaintenanceMode() {
    return $.getJSON('/api/config/maintenance-mode')
      .then(function (status) {
        maintenanceMode.enabled = Boolean(status.enabled || status.maintenance_mode);
        maintenanceMode.message = status.message || status.maintenance_message || maintenanceMode.message;
        return maintenanceMode;
      })
      .catch(function () { return maintenanceMode; });
  }

  /**
   * Lazily loads the Razorpay checkout SDK the first time it is needed.
   * Avoids background network calls to checkout-static-next.razorpay.com
   * on every page load for users who never top up.
   */
  function loadRazorpay() {
    return new Promise(function (resolve, reject) {
      if (typeof Razorpay !== 'undefined') { resolve(); return; }
      var script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = function () { resolve(); };
      script.onerror = function () { reject(new Error('Razorpay SDK failed to load.')); };
      document.head.appendChild(script);
    });
  }

  function loadCashfree() {
    return new Promise(function (resolve, reject) {
      if (typeof Cashfree !== 'undefined') { resolve(); return; }
      var script = document.createElement('script');
      script.src = 'https://sdk.cashfree.com/js/v3/cashfree.js';
      script.onload = function () { resolve(); };
      script.onerror = function () { reject(new Error('Cashfree SDK failed to load.')); };
      document.head.appendChild(script);
    });
  }

  function topUpWallet() {
    if (!currentUser) {
      pendingDestination = '';
      refreshFeatureFlags().then(function () {
        if (googleSignInEnabled) { showGoogleAuthModal(); } else { showSimpleLoginModal(); }
      });
      return;
    }

    if (maintenanceMode.enabled) { applyMaintenanceModeUi(); return; }

    if (rechargeModal) {
      if (rechargeAmountEl) { rechargeAmountEl.value = '50'; }
      // Default to Razorpay (most users/test runs expect Razorpay).
      if (rechargeGatewayEl) { rechargeGatewayEl.value = 'razorpay'; }
      rechargeModal.classList.add('open');
      setTimeout(function () {
        if (rechargeAmountEl) { rechargeAmountEl.focus(); rechargeAmountEl.select(); }
      }, 150);
    }
  }

  function startPaymentOrder(amount, gateway) {
    if (!Number.isFinite(amount) || amount <= 0) {
      showToast('Please enter a valid recharge amount.', 'warning');
      return;
    }
    gateway = gateway === 'cashfree' ? 'cashfree' : 'razorpay';

    setBusy(topUpButton, true);
    log('Starting secure ' + gatewayLabel(gateway) + ' payment…');

    var sdkLoader = gateway === 'cashfree' ? loadCashfree : loadRazorpay;
    sdkLoader()
      .then(function () {
        return $.ajax({
          url: '/user/wallet/payment/order',
          method: 'POST',
          contentType: 'application/json',
          data: JSON.stringify({ amount: amount, gateway: gateway })
        });
      })
      .then(function (order) {
        if (order && order.gateway === 'cashfree') {
          return openCashfreeCheckout(order);
        }
        return openRazorpayCheckout(order);
      })
      .catch(function (error) {
        console.error('Unable to create payment order:', error);
        // Handle Razorpay-specific business errors (e.g. international cards not allowed)
        var msg = readError(error, 'Unable to start payment.');
        try {
          var resp = error && (error.responseJSON || (error.responseText && JSON.parse(error.responseText)));
          if (resp && resp.error && (resp.error.reason || resp.error.code)) {
            var reason = String(resp.error.reason || resp.error.code).toLowerCase();
            if (reason.indexOf('international') !== -1 || reason.indexOf('international_transaction_not_allowed') !== -1) {
              msg = 'International cards are not supported by this merchant. Please use UPI/netbanking or contact support.';
            }
          }
        } catch (ignored) {}
        log(msg);
        showToast(msg, 'error');
      })
      .finally(function () {
        if (!maintenanceMode.enabled) setBusy(topUpButton, false);
      });
  }

  function gatewayLabel(gateway) {
    return gateway === 'cashfree' ? 'Cashfree' : 'Razorpay';
  }

  function openRazorpayCheckout(order) {
    console.log('Razorpay order from server:', order);
    if (!order || !order.keyId) {
      var msg = 'Missing Razorpay key id in order response.';
      console.error(msg, order);
      showToast(msg, 'error');
      return;
    }

    const options = {
      key:         order.keyId,
      amount:      order.amountPaise,
      currency:    order.currency,
      name:        order.name,
      description: order.description,
      order_id:    order.orderId,
      prefill:     { name: order.prefillName || '', contact: order.prefillContact || '' },
      theme:       { color: '#00457C' },
      handler:     function (response) { verifyRazorpayPayment(response); },
      modal:       { ondismiss: function () { log('Payment cancelled. Wallet was not credited.'); } }
    };

    const razorpay = new Razorpay(options);
    razorpay.on('payment.failed', function (response) {
      // Friendly handling for international-card rejection which Razorpay may report
      var message = response && response.error && response.error.description ? response.error.description : 'Payment failed. Wallet was not credited.';
      var reason = response && response.error && (response.error.reason || response.error.code || response.error.description) ? (response.error.reason || response.error.code) : null;
      if (reason && String(reason).toLowerCase().indexOf('international') !== -1) {
        message = 'International cards are not supported by this merchant. Please use UPI/netbanking or contact support.';
      }
      log(message);
      showToast(message, 'error');
    });
    razorpay.open();
  }

  function openCashfreeCheckout(order) {
    if (!order || !order.paymentSessionId) {
      throw new Error('Cashfree payment session was not received.');
    }
    var cashfree = Cashfree({ mode: order.gatewayMode || 'production' });
    return cashfree.checkout({
      paymentSessionId: order.paymentSessionId,
      redirectTarget: '_modal'
    }).then(function (result) {
      if (result && result.error) {
        throw new Error(result.error.message || 'Cashfree payment failed.');
      }
      var paymentId = result && result.paymentDetails && (result.paymentDetails.cfPaymentId || result.paymentDetails.paymentId);
      return verifyCashfreePayment(order.orderId, paymentId || '');
    });
  }

  function verifyCashfreePayment(orderId, paymentId) {
    log('Verifying Cashfree payment securely…');
    return $.ajax({
      url: '/user/wallet/payment/verify',
      method: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({
        gateway: 'cashfree',
        cashfreeOrderId: orderId,
        cashfreePaymentId: paymentId
      })
    })
      .then(function (result) {
        if (result.wallet) updateWallet(result.wallet);
        refreshHistory();
        var msg = result.message || 'Payment verified and wallet credited successfully.';
        log(msg);
        showToast(msg, 'success');
      });
  }

  function verifyRazorpayPayment(response) {
    log('Verifying payment securely…');
    $.ajax({
      url: '/user/wallet/payment/verify',
      method: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({
        gateway: 'razorpay',
        razorpayOrderId:   response.razorpay_order_id,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature
      })
    })
      .then(function (result) {
        if (result.wallet) updateWallet(result.wallet);
        refreshHistory();
        var msg = result.message || 'Payment verified and wallet credited successfully.';
        log(msg);
        showToast(msg, 'success');
      })
      .catch(function (error) {
        console.error('Payment verification failed:', error);
        var msg = readError(error, 'Payment verification failed. Wallet was not credited.');
        log(msg);
        showToast(msg, 'error');
      });
  }

  // ─── History ─────────────────────────────────────────────────────────────

  function refreshHistory() {
    $.getJSON('/user/history').then(renderHistory).catch(function (error) {
      console.error('Unable to load history:', error);
    });
  }

  function renderHistory(items) {
    if (!items || items.length === 0) {
      historyList.innerHTML = '<p class="empty-state">No calls yet.</p>';
      return;
    }
    historyList.innerHTML = items.map(function (item) {
      var dateStr = item.createdAt ? new Date(item.createdAt).toLocaleString() : '';
      return '<article class="history-item">' +
        '<div class="history-main"><span>' + escapeHtml(item.destination || 'unknown') + '</span><strong>₹' + money(item.amountCharged) + '</strong></div>' +
        '<div class="history-meta">' + item.durationSeconds + 's billed' +
        ' · Wallet ₹' + money(item.walletAfter) +
        ' · ' + escapeHtml(item.disconnectReason || item.status || 'completed') +
        (dateStr ? ' · ' + escapeHtml(dateStr) : '') +
        '</div>' +
        '</article>';
    }).join('');
  }

  // ─── Wallet UI ───────────────────────────────────────────────────────────

  function updateWalletFromSession(session) {
    // Use cached wallet data for minimumBalance/ratePerMinute if available;
    // refreshWallet() will update with server values momentarily
    var minBalance = wallet && wallet.minimumBalance ? wallet.minimumBalance : 1;
    var rate       = wallet && wallet.ratePerMinute  ? wallet.ratePerMinute  : 10;
    var balance    = Number(session.walletBalance) || 0;
    updateWallet({
      walletBalance:  balance,
      minimumBalance: minBalance,
      ratePerMinute:  rate,
      canCall:        balance >= minBalance
    });
  }

  function updateWallet(walletData) {
    wallet = walletData;
    if (walletBalance) walletBalance.textContent = '₹' + money(walletData.walletBalance);
    if (walletRate)    walletRate.textContent    = '₹' + money(walletData.ratePerMinute) + '/min';
  }

  function applyMaintenanceModeUi() {
    if (!maintenanceMode.enabled) return;
    const message    = maintenanceMode.message || 'The application is currently under maintenance.';
    const callButton = document.getElementById('button-call');
    if (callButton)       { callButton.disabled  = true; callButton.title  = message; }
    if (topUpButton)      { topUpButton.disabled  = true; topUpButton.title  = message; }
    if (phoneNumberInput) { phoneNumberInput.disabled = true; phoneNumberInput.placeholder = 'Maintenance mode active'; }
    setDeviceStatus('Maintenance', 'error');
    log(message);
  }

  // ─── Location ────────────────────────────────────────────────────────────

  function requestLocation() {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      function (position) {
        const coords = position.coords.latitude.toFixed(5) + ',' + position.coords.longitude.toFixed(5);
        if (profileLocation) profileLocation.value = coords;
        if (simpleLoginLocationEl) simpleLoginLocationEl.value = coords;
      },
      function () {
        if (profileLocation) profileLocation.value = '';
        if (simpleLoginLocationEl) simpleLoginLocationEl.value = '';  // server defaults to "x"
      },
      { enableHighAccuracy: false, timeout: 4000, maximumAge: 300000 }
    );
  }

  // ─── Audio devices ───────────────────────────────────────────────────────

  function setupAudioDeviceSelection() {
    if (!device.audio) return;
    device.audio.on('deviceChange', updateAllDevices);
    if (device.audio.isOutputSelectionSupported) {
      document.getElementById('output-selection').style.display = 'block';
      updateAllDevices();
    }
  }

  function updateAllDevices() {
    if (!device || !device.audio || !device.audio.availableOutputDevices) return;
    updateDevices(speakerDevices, device.audio.speakerDevices.get());
    updateDevices(ringtoneDevices, device.audio.ringtoneDevices.get());
  }

  function updateDevices(selectEl, selectedDevices) {
    selectEl.innerHTML = '';
    device.audio.availableOutputDevices.forEach(function (deviceInfo, id) {
      let isActive = selectedDevices.size === 0 && id === 'default';
      selectedDevices.forEach(function (sel) { if (sel.deviceId === id) isActive = true; });
      const option = document.createElement('option');
      option.label = deviceInfo.label;
      option.setAttribute('data-id', id);
      if (isActive) option.setAttribute('selected', 'selected');
      selectEl.appendChild(option);
    });
  }

  // ─── Volume meters ───────────────────────────────────────────────────────

  function updateVolumeBar(volumeBar, volume) {
    let color = 'red';
    if (volume < 0.50)      color = 'var(--success)';
    else if (volume < 0.75) color = 'var(--warning)';
    volumeBar.style.width      = Math.min(100, Math.floor(volume * 100)) + '%';
    volumeBar.style.background = color;
  }

  // ─── Dialpad ─────────────────────────────────────────────────────────────

  function handleDialKey(key) {
    if (!key) return;
    // While in a call, send DTMF tones
    if (activeCall && typeof activeCall.sendDigits === 'function') {
      activeCall.sendDigits(key);
      log('Sent DTMF: ' + key);
      return;
    }
    // Append digit; trigger brief scale animation to confirm input
    phoneNumberInput.value = phoneNumberInput.value + key;
    formatPhoneNumber();
    phoneNumberInput.classList.remove('digit-added');
    void phoneNumberInput.offsetWidth; // force reflow for animation restart
    phoneNumberInput.classList.add('digit-added');
  }

  /**
   * Sanitize phone number - extract only digits, keep country code if present
   * Handles: 9876543210, +91 9876543210, (91) 9876543210, 98765-43210, etc.
   */
  function sanitizePhoneNumber(input) {
    if (!input) return '';
    // Remove all non-digit characters except leading +
    let cleaned = input.trim().replace(/[^\d+]/g, '');
    // If starts with +, keep it, otherwise remove all +
    if (cleaned.startsWith('+')) {
      cleaned = '+' + cleaned.substring(1).replace(/\+/g, '');
    } else {
      cleaned = cleaned.replace(/\+/g, '');
    }
    // Validate: must have at least 10 digits
    const digitsOnly = cleaned.replace(/\D/g, '');
    if (digitsOnly.length < 10) return '';
    return cleaned;
  }

  /**
   * Format phone number for display: 00000-00000 for 10-digit Indian numbers
   * Preserves country code display
   */
  function formatPhoneDisplay(number) {
    if (!number) return '';
    const digitsOnly = number.replace(/\D/g, '');
    
    // If exactly 10 digits, format as 00000-00000
    if (digitsOnly.length === 10) {
      return digitsOnly.slice(0, 5) + '-' + digitsOnly.slice(5);
    }
    
    // If starts with country code (more than 10 digits)
    if (digitsOnly.length > 10) {
      const countryCode = digitsOnly.slice(0, -10);
      const main = digitsOnly.slice(-10);
      return '+' + countryCode + ' ' + main.slice(0, 5) + '-' + main.slice(5);
    }
    
    return number;
  }

  /**
   * Auto-format the phone number input as user types
   */
  function formatPhoneNumber() {
    const raw = phoneNumberInput.value;
    const sanitized = sanitizePhoneNumber(raw);
    if (sanitized) {
      phoneNumberInput.value = formatPhoneDisplay(sanitized);
    }
  }

  /**
   * Validate if phone number is ready for calling
   */
  function isValidPhoneNumber(number) {
    if (!number) return false;
    const digitsOnly = number.replace(/\D/g, '');
    // Must have at least 10 digits
    return digitsOnly.length >= 10;
  }

  // ─── Status pill ─────────────────────────────────────────────────────────

  function setDeviceStatus(text, state) {
    if (!deviceStatus) return;
    deviceStatus.textContent = text;
    deviceStatus.className   = 'status-pill status-' + state;
  }

  function resetCallUi() {
    releaseWakeLock();   // allow screen to sleep again
    document.getElementById('button-call').style.display   = 'inline';
    document.getElementById('button-hangup').style.display = 'none';
    volumeIndicators.style.display = 'none';
    setCallState(null);
    stopCallTimer();
  }

  // ─── Log ─────────────────────────────────────────────────────────────────

  function log(message) {
    const logDiv    = document.getElementById('log');
    const timestamp = new Date().toLocaleTimeString();
    logDiv.innerHTML += '<p>&gt;&nbsp;[' + timestamp + '] ' + escapeHtml(message) + '</p>';
    logDiv.scrollTop  = logDiv.scrollHeight;
  }

  function setClientNameUI(clientName) {
    const div = document.getElementById('client-name');
    div.innerHTML = 'Your client name: <strong>' + escapeHtml(clientName) + '</strong>';
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  function money(value) {
    return Number(value || 0).toFixed(2);
  }

  function readError(error, fallback) {
    if (error && error.responseJSON) {
      if (error.responseJSON.message) return error.responseJSON.message;
      if (error.responseJSON.error)   return error.responseJSON.error;
    }
    if (error && error.responseText) {
      try {
        const parsed = JSON.parse(error.responseText);
        return parsed.message || parsed.error || fallback;
      } catch (ignored) {}
      return error.responseText.substring(0, 200);
    }
    if (error && error.status === 401) return 'Session expired. Please sign in again.';
    if (error && error.status === 403) return 'Access denied. Please contact support.';
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

  // ─── Screen Wake Lock ─────────────────────────────────────────────────────
  //
  // Goal: keep the screen (and browser tab) alive during an active PSTN call
  // so audio does not drop when the user's device tries to sleep.
  //
  // What IS possible:
  //   • Android Chrome / Edge (≥ 84): Screen Wake Lock API works well.
  //     The screen stays on and the Twilio WebRTC stream keeps running.
  //   • Desktop Chrome / Edge: Wake Lock works; backgrounding to another app
  //     still keeps the tab audio running because desktop OSes do not freeze
  //     background browser tabs the same way.
  //
  // What is NOT reliably possible (browser / OS limits, not the SDK):
  //   • iOS Safari / Chrome on iPhone – iOS freezes JavaScript and suspends
  //     WebAudio / WebRTC when the browser goes to the background or the
  //     screen turns off. The Wake Lock API is not available on iOS Safari.
  //     There is NO supported workaround for this at the browser level.
  //     Native PSTN apps must use CallKit (iOS) for background audio, which
  //     is only available to native apps — not web apps.
  //   • Android WebView or non-Chrome browsers – may throttle or kill JS
  //     execution after the screen turns off. Behaviour is vendor-specific.
  //
  // Best supported behaviour implemented here:
  //   1. Request a Screen Wake Lock when a call is answered → screen stays on.
  //   2. Release the lock when the call ends.
  //   3. Re-acquire the lock if the page regains visibility while still in call.
  //   4. Warn the user (toast) if they leave the tab during an active call.

  async function requestWakeLock() {
    if (!('wakeLock' in navigator)) return;   // API not available (iOS, old browsers)
    try {
      wakeLockSentinel = await navigator.wakeLock.request('screen');
      wakeLockSentinel.addEventListener('release', function () {
        wakeLockSentinel = null;
      });
    } catch (err) {
      // Non-critical: wake lock may fail if the page is not visible yet
      console.warn('Wake lock request failed:', err.name, err.message);
    }
  }

  function releaseWakeLock() {
    if (wakeLockSentinel) {
      wakeLockSentinel.release().catch(function () {});
      wakeLockSentinel = null;
    }
  }

  // ─── Background-call visibility handler ──────────────────────────────────
  // Warns users who leave the tab during a call (especially important on iOS
  // where the call WILL drop when the screen turns off).

  function initBackgroundCallSupport() {
    document.addEventListener('visibilitychange', function () {
      if (document.hidden && activeCall) {
        showToast('Keep this tab visible for best call quality.', 'warning', 5000);
      }
      // Re-acquire wake lock if the page becomes visible again mid-call.
      // Browsers automatically release the wake lock when the page is hidden.
      if (!document.hidden && activeCall && !wakeLockSentinel) {
        requestWakeLock();
      }
    });
  }

});
