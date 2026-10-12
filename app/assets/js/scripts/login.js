/**
 * Script for login.ejs
 */
// Validation Regexes.
const validUsername         = /^[a-zA-Z0-9_]{3,16}$/

// Login Elements
const loginCancelContainer  = document.getElementById('loginCancelContainer')
const loginCancelButton     = document.getElementById('loginCancelButton')
const loginEmailError       = document.getElementById('loginEmailError')
const loginUsername         = document.getElementById('loginUsername')
const loginPasswordError    = document.getElementById('loginPasswordError')
const loginPassword         = document.getElementById('loginPassword')
const loginPasswordRepeatContainer = document.getElementById('loginPasswordRepeatContainer')
const loginPasswordRepeatError     = document.getElementById('loginPasswordRepeatError')
const loginPasswordRepeat   = document.getElementById('loginPasswordRepeat')
const loginModeToggle       = document.getElementById('loginModeToggle')
const loginSubheader        = document.getElementById('loginSubheader')
const loginButton           = document.getElementById('loginButton')
const loginForm             = document.getElementById('loginForm')

// True when the form creates a new server (AuthMe) account instead of logging in.
let loginRegisterMode = false


/**
 * Show a login error.
 * 
 * @param {HTMLElement} element The element on which to display the error.
 * @param {string} value The error text.
 */
function showError(element, value){
    element.innerHTML = value
    element.style.opacity = 1
}

/**
 * Shake a login error to add emphasis.
 * 
 * @param {HTMLElement} element The element to shake.
 */
function shakeError(element){
    if(element.style.opacity == 1){
        element.classList.remove('shake')
        void element.offsetWidth
        element.classList.add('shake')
    }
}

/**
 * Enable the login button only when every visible field is valid.
 */
function updateLoginEnabled(){
    const usernameOk = validUsername.test(loginUsername.value)
    const passwordOk = loginPassword.value.length > 0
    const repeatOk = !loginRegisterMode || loginPasswordRepeat.value === loginPassword.value
    loginDisabled(!(usernameOk && passwordOk && repeatOk))
}

/**
 * Validate that the username field is neither empty nor invalid.
 * 
 * @param {string} value The username value.
 */
function validateEmail(value){
    if(value){
        if(!validUsername.test(value)){
            showError(loginEmailError, Lang.queryJS('login.error.invalidValue'))
        } else {
            loginEmailError.style.opacity = 0
        }
    } else {
        showError(loginEmailError, Lang.queryJS('login.error.requiredValue'))
    }
    updateLoginEnabled()
}

/**
 * Validate that the password field is not empty.
 * 
 * @param {string} value The password value.
 */
function validatePassword(value){
    if(value){
        loginPasswordError.style.opacity = 0
    } else {
        showError(loginPasswordError, Lang.queryJS('login.error.requiredValue'))
    }
    updateLoginEnabled()
}

/**
 * Validate that the repeated password matches (register mode only).
 */
function validatePasswordRepeat(){
    if(loginRegisterMode && loginPasswordRepeat.value !== loginPassword.value){
        showError(loginPasswordRepeatError, Lang.queryJS('login.error.passwordMismatch'))
    } else {
        loginPasswordRepeatError.style.opacity = 0
    }
    updateLoginEnabled()
}

/**
 * Switch between logging in and creating a new server account.
 * 
 * @param {boolean} register True for register mode.
 */
function setLoginRegisterMode(register){
    loginRegisterMode = register
    loginPasswordRepeatContainer.style.display = register ? '' : 'none'
    loginPasswordRepeat.value = ''
    loginPasswordRepeatError.style.opacity = 0
    loginSubheader.innerHTML = Lang.queryJS(register ? 'login.registerHeader' : 'login.loginHeader')
    loginModeToggle.innerHTML = Lang.queryJS(register ? 'login.haveAccount' : 'login.needAccount')
    updateLoginEnabled()
}

function clearLoginForm(){
    loginUsername.value = ''
    loginPassword.value = ''
    loginPasswordRepeat.value = ''
    setLoginRegisterMode(false)
}

loginModeToggle.addEventListener('click', (e) => {
    e.preventDefault()
    setLoginRegisterMode(!loginRegisterMode)
})

// Emphasize errors with shake when focus is lost.
loginUsername.addEventListener('focusout', (e) => {
    validateEmail(e.target.value)
    shakeError(loginEmailError)
})
loginPassword.addEventListener('focusout', (e) => {
    validatePassword(e.target.value)
    shakeError(loginPasswordError)
})
loginPasswordRepeat.addEventListener('focusout', () => {
    validatePasswordRepeat()
    shakeError(loginPasswordRepeatError)
})

// Validate input for each field.
loginUsername.addEventListener('input', (e) => {
    validateEmail(e.target.value)
})
loginPassword.addEventListener('input', (e) => {
    validatePassword(e.target.value)
    if(loginRegisterMode && loginPasswordRepeat.value){
        validatePasswordRepeat()
    }
})
loginPasswordRepeat.addEventListener('input', () => {
    validatePasswordRepeat()
})

/**
 * Enable or disable the login button.
 * 
 * @param {boolean} v True to enable, false to disable.
 */
function loginDisabled(v){
    if(loginButton.disabled !== v){
        loginButton.disabled = v
    }
}

/**
 * Enable or disable loading elements.
 * 
 * @param {boolean} v True to enable, false to disable.
 */
function loginLoading(v){
    if(v){
        loginButton.setAttribute('loading', v)
        loginButton.innerHTML = loginButton.innerHTML.replace(Lang.queryJS('login.login'), Lang.queryJS('login.loggingIn'))
    } else {
        loginButton.removeAttribute('loading')
        loginButton.innerHTML = loginButton.innerHTML.replace(Lang.queryJS('login.loggingIn'), Lang.queryJS('login.login'))
    }
}

/**
 * Enable or disable login form.
 * 
 * @param {boolean} v True to enable, false to disable.
 */
function formDisabled(v){
    loginDisabled(v)
    loginCancelButton.disabled = v
    loginUsername.disabled = v
    loginPassword.disabled = v
    loginPasswordRepeat.disabled = v
}

let loginViewOnSuccess = VIEWS.landing
let loginViewOnCancel = VIEWS.settings
let loginViewCancelHandler

function loginCancelEnabled(val){
    if(val){
        $(loginCancelContainer).show()
    } else {
        $(loginCancelContainer).hide()
    }
}

loginCancelButton.onclick = (e) => {
    switchView(getCurrentView(), loginViewOnCancel, 500, 500, () => {
        clearLoginForm()
        loginCancelEnabled(false)
        if(loginViewCancelHandler != null){
            loginViewCancelHandler()
            loginViewCancelHandler = null
        }
    })
}

// Disable default form behavior.
loginForm.onsubmit = () => { return false }

// Bind login button behavior.
loginButton.addEventListener('click', () => {
    // Disable form.
    formDisabled(true)

    // Show loading stuff.
    loginLoading(true)

    AuthManager.addOfflineAccount(loginUsername.value, loginPassword.value, loginRegisterMode).then((value) => {
        updateSelectedAccount(value)
        loginButton.innerHTML = loginButton.innerHTML.replace(Lang.queryJS('login.loggingIn'), Lang.queryJS('login.success'))
        $('.circle-loader').toggleClass('load-complete')
        $('.checkmark').toggle()
        setTimeout(() => {
            switchView(VIEWS.login, loginViewOnSuccess, 500, 500, async () => {
                // Temporary workaround
                if(loginViewOnSuccess === VIEWS.settings){
                    await prepareSettings()
                }
                loginViewOnSuccess = VIEWS.landing // Reset this for good measure.
                loginCancelEnabled(false) // Reset this for good measure.
                loginViewCancelHandler = null // Reset this for good measure.
                clearLoginForm()
                $('.circle-loader').toggleClass('load-complete')
                $('.checkmark').toggle()
                loginLoading(false)
                loginButton.innerHTML = loginButton.innerHTML.replace(Lang.queryJS('login.success'), Lang.queryJS('login.login'))
                formDisabled(false)
            })
        }, 1000)
    }).catch((displayableError) => {
        loginLoading(false)

        let actualDisplayableError
        if(isDisplayableError(displayableError)) {
            msftLoginLogger.error('Error while logging in.', displayableError)
            actualDisplayableError = displayableError
        } else {
            // Uh oh.
            msftLoginLogger.error('Unhandled error during login.', displayableError)
            actualDisplayableError = Lang.queryJS('login.error.unknown')
        }

        setOverlayContent(actualDisplayableError.title, actualDisplayableError.desc, Lang.queryJS('login.tryAgain'))
        setOverlayHandler(() => {
            formDisabled(false)
            toggleOverlay(false)
        })
        toggleOverlay(true)
    })

})