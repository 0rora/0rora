function loginForm() {
    new mdc.textField.MDCTextField(document.querySelector('.username'));
    new mdc.textField.MDCTextField(document.querySelector('.password'));
    new mdc.ripple.MDCRipple(document.querySelector('.cancel'));
    new mdc.ripple.MDCRipple(document.querySelector('.next'));

    document.querySelector('.cancel').addEventListener('click', function () {
        document.querySelector('#login-form').reset();
    });
}

function topAppBar() {
    mdc.topAppBar.MDCTopAppBar.attachTo(document.querySelector('.mdc-top-app-bar'));
}

function snackattack(message) {
    new mdc.snackbar.MDCSnackbar(document.querySelector('.mdc-snackbar'))
        .show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

function drawer() {
    var list = mdc.list.MDCList.attachTo(document.querySelector('.mdc-list'));
    list.wrapFocus = true;
}