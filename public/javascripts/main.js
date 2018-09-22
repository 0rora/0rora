let smallForm = window.matchMedia("(max-width: 767px)").matches;

function loginForm() {
    new mdc.textField.MDCTextField($('.username'));
    new mdc.textField.MDCTextField($('.password'));
    let $cancel = $('.cancel');
    new mdc.ripple.MDCRipple($cancel);
    new mdc.ripple.MDCRipple($('.next'));

    $cancel.click(function () {
        $('#login-form').reset();
    });
}

var modalDrawer;

function topAppBar() {
    const topAppBar = mdc.topAppBar.MDCTopAppBar.attachTo(document.querySelector('.mdc-top-app-bar'));
    topAppBar.setScrollTarget(document.querySelector('.drawer-main-content'));
    topAppBar.listen('MDCTopAppBar:nav', () => {
        modalDrawer.open = !modalDrawer.open;
    });
}

function snackattack(message) {
    new mdc.snackbar.MDCSnackbar($('.mdc-snackbar'))
        .show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

function drawer() {
    modalDrawer = mdc.drawer.MDCDrawer.attachTo(document.querySelector('.mdc-drawer--modal'));
    let list = mdc.list.MDCList.attachTo(document.querySelector('.mdc-list--permanent'));
    list.wrapFocus = true;
    changedMedia();
}

function resized() {
    let smallForm_ = window.matchMedia("(max-width: 767px)").matches;
    if (smallForm !== smallForm_) {
        smallForm = smallForm_;
        changedMedia();
    }
}

function changedMedia() {
    let drawerButton = $('.mdc-top-app-bar__row > section > button');
    if (smallForm) {
        $('.mdc-drawer--permanent').hide();
        drawerButton.show();
    } else {
        $('.mdc-drawer--permanent').show();
        drawerButton.hide();
    }
}

let originalOnload = window.onload;
window.onload = function() {
    if (originalOnload) {
        originalOnload();
    }
    $(window).resize(resized);
    topAppBar();
    drawer();
};