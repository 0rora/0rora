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

var modalDrawer;

function topAppBar() {
    if (!document.querySelector('.mdc-top-app-bar')) return;
    const topAppBar = mdc.topAppBar.MDCTopAppBar.attachTo(document.querySelector('.mdc-top-app-bar'));
    topAppBar.setScrollTarget(document.querySelector('.drawer-main-content'));
    topAppBar.listen('MDCTopAppBar:nav', () => {
        modalDrawer.open = !modalDrawer.open;
    });
}

function snackattack(message) {
    new mdc.snackbar.MDCSnackbar(document.querySelector('.mdc-snackbar'))
        .show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

function drawer() {
    if (!document.querySelector('.mdc-drawer--modal')) return;
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

let droppedFiles = false;
function enableFileDragAndDrop() {
    if (!document.querySelector('#upload-form')) return;
    let $form = $('#upload-form');
    console.log("enable d&d");
    $form.on('drag dragstart dragend dragover dragenter dragleave drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
    }).on('dragenter', function() {
        $form.addClass('is-dragover'); // todo - to be defined
        console.log("enter");
    }).on('dragleave dragend drop', function() {
        $form.removeClass('is-dragover'); // todo - to be defined
        console.log("leave");
    }).on('drop', function(e) {
        console.log("dropping");
        droppedFiles = e.originalEvent.dataTransfer.files;
        console.log(droppedFiles);
        $form.trigger('submit');
    });

    $form.on('submit', function(e) {
        console.log("submitting");
        if ($form.hasClass('is-uploading')) return false;
        $form.addClass('is-uploading');
        e.preventDefault();
        const ajaxData = new FormData($form.get(0));
        if (droppedFiles) {
            $.each(droppedFiles, function(i, file) {
                console.log("pushing file ", file);
                ajaxData.append($form.find('input[type="file"]').attr('name'), file);
            });
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
                $form.removeClass('is-uploading');
            },
            success: function(data) {
                $form.addClass(data.success ? 'is-success' : 'is-error' );
                if (!data.success) {
                    console.log(data.error);
                }
            },
            error: function() {
                console.log("error?");
            }
        });
    });
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
};