(function () {
    'use strict';

    // Base64URL helpers
    function base64urlEncode(buffer) {
        var bytes = new Uint8Array(buffer);
        var str = '';
        for (var i = 0; i < bytes.length; i++) str += String.fromCharCode(bytes[i]);
        return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    function base64urlDecode(str) {
        str = str.replace(/-/g, '+').replace(/_/g, '/');
        while (str.length % 4) str += '=';
        var binary = atob(str);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    }

    function getCsrf() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        return {
            token: token ? token.content : '',
            header: header ? header.content : 'X-CSRF-TOKEN'
        };
    }

    function getBasePath() {
        var meta = document.querySelector('meta[name="_kyc_base"]');
        var base = meta ? meta.content : '/';
        // Ensure it ends without trailing slash for clean concatenation
        return base.replace(/\/+$/, '');
    }

    function fetchJson(url, body) {
        var csrf = getCsrf();
        var headers = { 'Content-Type': 'application/json' };
        headers[csrf.header] = csrf.token;
        return fetch(getBasePath() + url, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(body),
            credentials: 'same-origin'
        }).then(function (r) {
            if (!r.ok) throw new Error('Request failed: ' + r.status);
            return r.json();
        });
    }

    if (!window.PublicKeyCredential) return;

    var passkeyBtn = document.getElementById('passkey-login-btn');
    var divider = document.getElementById('passkey-divider');
    if (passkeyBtn) passkeyBtn.style.display = '';
    if (divider) divider.style.display = '';

    // Passkey button click
    if (passkeyBtn) {
        passkeyBtn.addEventListener('click', function () {
            var emailInput = document.querySelector('input[name="username"]');
            var email = emailInput ? emailInput.value : '';

            fetchJson('/api/passkeys/authenticate/options', { email: email }).then(function (options) {
                var publicKey = {
                    challenge: base64urlDecode(options.challenge),
                    rpId: options.rpId,
                    timeout: options.timeout,
                    userVerification: options.userVerification
                };

                if (options.allowCredentials && options.allowCredentials.length > 0) {
                    publicKey.allowCredentials = options.allowCredentials.map(function (c) {
                        return { type: c.type, id: base64urlDecode(c.id), transports: c.transports };
                    });
                }

                return navigator.credentials.get({ publicKey: publicKey });
            }).then(function (credential) {
                return completeAuthentication(credential);
            }).catch(function (err) {
                if (err.name !== 'AbortError' && err.name !== 'NotAllowedError') {
                    console.error('Passkey authentication error:', err);
                }
            });
        });
    }

    function completeAuthentication(credential) {
        var body = {
            id: base64urlEncode(credential.rawId),
            rawId: base64urlEncode(credential.rawId),
            type: credential.type,
            clientDataJSON: base64urlEncode(credential.response.clientDataJSON),
            authenticatorData: base64urlEncode(credential.response.authenticatorData),
            signature: base64urlEncode(credential.response.signature),
            userHandle: credential.response.userHandle ?
                base64urlEncode(credential.response.userHandle) : null
        };

        return fetchJson('/api/passkeys/authenticate/verify', body).then(function () {
            var uiBase = document.querySelector('meta[name="_ui_base"]');
            window.location.href = (uiBase ? uiBase.content : '') + '/login';
        }).catch(function (err) {
            console.error('Passkey verification failed:', err);
            // Show error in form
            var errorEl = document.querySelector('.form-error');
            if (!errorEl) {
                errorEl = document.createElement('p');
                errorEl.className = 'form-error';
                errorEl.setAttribute('aria-live', 'assertive');
                var actions = document.querySelector('.auth-actions');
                if (actions) actions.parentNode.insertBefore(errorEl, actions);
            }
            errorEl.textContent = 'Passkey authentication failed. Please try again.';
        });
    }
})();
