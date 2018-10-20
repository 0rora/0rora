let smallForm = window.matchMedia("(max-width: 767px)").matches;

function loginForm() {
    new mdc.textField.MDCTextField(document.querySelector('.username'));
    new mdc.textField.MDCTextField(document.querySelector('.password'));
    let $cancel = document.querySelector('.cancel');
    new mdc.ripple.MDCRipple($cancel);
    new mdc.ripple.MDCRipple(document.querySelector('.next'));

    $cancel.click(function () {
        document.querySelector('#login-form').reset();
    });
}

let modalDrawer;

function topAppBar() {
    if (!document.querySelector('.mdc-top-app-bar')) return;
    const topAppBar = mdc.topAppBar.MDCTopAppBar.attachTo(document.querySelector('.mdc-top-app-bar'));
    topAppBar.setScrollTarget(document.querySelector('.drawer-main-content'));
    topAppBar.listen('MDCTopAppBar:nav', () => {
        modalDrawer.open = !modalDrawer.open;
    });
    document.querySelector(".mdc-list").addEventListener('click', (event) => {
        modalDrawer.open = false;
    });
}

let snackbar = null;
function snackattack(message) {
    if (!snackbar) snackbar = new mdc.snackbar.MDCSnackbar(document.querySelector('.mdc-snackbar'));
    snackbar.show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

function drawer() {
    if (!document.querySelector('.mdc-drawer--modal')) return;
    modalDrawer = mdc.drawer.MDCDrawer.attachTo(document.querySelector('.mdc-drawer--modal'));
    let list = mdc.list.MDCList.attachTo(document.querySelector('.mdc-list--permanent'));
    list.wrapFocus = true;
    changedMedia();
}

function paymentsList() {
    if (!document.querySelector('#payments-list')) return;
    let listEle = document.getElementById('#payments-list');
    let list = new mdc.list.MDCList(listEle);
}

function loadPayments() {
    const template = $('#payment-template').html();
    $.ajax({
        url: '/payments/success',
        type: 'GET',
        success: function(data) {
            const rendered = Mustache.render(template, { payments: data });
            $('#payments-list').html(rendered);
        },
        error: function(xhr) {
            console.log("xhr: ", xhr);
        }
    });
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
        modalDrawer.open = false;
    }
}

let droppedFiles = false;
function enableFileDragAndDrop() {
    if (!document.querySelector('#upload-form')) return;

    let $form = $('#upload-form');
    let $media = $('.csv-upload-media');
    $('#csv-upload-button').click(function () { $(".csv-upload-input").trigger("click"); });
    $('#csv-upload-card').click(function () { $(".csv-upload-input").trigger("click"); });
    $('.csv-upload-input').change(function () { $form.trigger('submit'); });

    $form.on('drag dragstart dragend dragover dragenter dragleave drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
    }).on('dragenter', function() {
        $form.addClass('is-dragover');
    }).on('dragleave dragend drop', function() {
        $form.removeClass('is-dragover');
    }).on('drop', function(e) {
        droppedFiles = e.originalEvent.dataTransfer.files;
        $form.trigger('submit');
    });

    $form.on('submit', function(e) {
        console.log("submitting");
        if ($media.hasClass('csv-is-uploading')) return false;
        $media.addClass('csv-is-uploading');
        e.preventDefault();
        const ajaxData = new FormData($form.get(0));
        if (droppedFiles) {
            $.each(droppedFiles, function(i, file) { ajaxData.set('csv_file', file); });
        }
        $.ajax({
            url: $form.attr('action'),
            type: $form.attr('method'),
            data: ajaxData,
            dataType: 'json',
            cache: false,
            contentType: false,
            processData: false,
            complete: function() {
                $media.removeClass('csv-is-uploading');
                droppedFiles = false;
                $('.csv-upload-input').val("");
            },
            success: function(data) {
                $media.addClass(data.success ? 'is-success' : 'is-error' );
                console.log("data: ", data);
                snackattack("Submitted " + data.count + " payment" + (data.count === 1 ? "" : "s"));
                if (!data.success) {
                    console.log(data.msg);
                }
            },
            error: function(xhr) {
                console.log("xhr: ", xhr);
            }
        });
    });
}

let dashboardFocus = null;
function switchDashboardFocusTo(section) {
    if (dashboardFocus) dashboardFocus.addClass('hidden');
    dashboardFocus = $("#section_" + section);
    dashboardFocus.removeClass('hidden');
    $('.mdc-top-app-bar__title').text(dashboardFocus.attr('title'));
    if (section === "payments") loadPayments();
}

function hashChanged(e) {
    switchDashboardFocusTo(window.location.hash.substr(1));
}

let originalOnload = window.onload;
window.onload = function() {
    if (originalOnload) {
        originalOnload();
    }
    $(window).resize(resized);
    topAppBar();
    drawer();
    enableFileDragAndDrop();
    if ($('#login-form').length === 0) {
        window.onhashchange = hashChanged;
        window.location.hash = "#payments";
        hashChanged();
    }
};