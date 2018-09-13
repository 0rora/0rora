const usernameField = new mdc.textField.MDCTextField(document.querySelector('.username'));
const passwordField = new mdc.textField.MDCTextField(document.querySelector('.password'));
new mdc.ripple.MDCRipple(document.querySelector('.cancel'));
new mdc.ripple.MDCRipple(document.querySelector('.next'));

document.querySelector('.cancel').addEventListener('click', function() {
    document.querySelector('#login-form').reset();
});

function snackattack(message) {
    new mdc.snackbar.MDCSnackbar(document.querySelector('.mdc-snackbar'))
        .show({ message: message, actionText: 'OK', actionHandler: function () {} });
}
