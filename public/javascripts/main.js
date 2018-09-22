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
    // $('.mdc-top-app-bar__navigation-icon').click((event) => {
    //     console.log(event);
    //     drawer.open = true;
    // })
}

function snackattack(message) {
    new mdc.snackbar.MDCSnackbar($('.mdc-snackbar'))
        .show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

function drawer() {
    modalDrawer = mdc.drawer.MDCDrawer.attachTo(document.querySelector('.mdc-drawer--modal'));
    // let list = mdc.list.MDCList.attachTo(document.querySelector('.mdc-list--permanent'));
    // list.wrapFocus = true;
    // document.querySelector('.mdc-drawer-scrim').addEventListener('click', (event) => {
    //     console.log(event);
    //     modalDrawer.open = false;
    // });
    // changedMedia();
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
    // if (smallForm) {
    //     $('.mdc-drawer--modal').show();
    //     $('.mdc-drawer--permanent').hide();
    //     $('.mdc-drawer-scrim').show();
    //     $('.mdc-top-app-bar').removeClass('mdc-top-app-bar--fixed');
    //     drawerButton.show();
    // } else {
    //     $('.mdc-drawer--modal').hide();
    //     $('.mdc-drawer--permanent').show();
    //     $('.mdc-drawer-scrim').hide();
    //     $('.mdc-top-app-bar').addClass('mdc-top-app-bar--fixed');
    //     drawerButton.hide();
    // }
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