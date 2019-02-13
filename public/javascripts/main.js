let smallForm = window.matchMedia("(max-width: 767px)").matches;
const dateFormatter = new Intl.DateTimeFormat(Intl.DateTimeFormat().resolvedOptions().locale,
    { year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', hour12: true });

const drawer = document.querySelector('.mdc-drawer');
const list = document.querySelector('.mdc-list');
let activeDrawer;
let activeList;

const topAppBar = mdc.topAppBar.MDCTopAppBar.attachTo(document.getElementById('app-bar'));
topAppBar.listen('MDCTopAppBar:nav', () => {
    if (typeof activeDrawer !== 'undefined') {
        activeDrawer.open = !activeDrawer.open;
    }
});


const actualResizeHandler = () => {
    let drawerButton = $('.mdc-top-app-bar__row > section > button');
    if (window.matchMedia('(max-width: 767px)').matches) {
        if (typeof activeList !== 'undefined') {
            activeList.destroy();
        }
        drawer.classList.add('mdc-drawer--modal');
        activeDrawer = mdc.drawer.MDCDrawer.attachTo(drawer);
        drawerButton.show();
    } else {
        if (typeof activeDrawer !== 'undefined') {
            activeDrawer.destroy();
        }
        drawer.classList.remove('mdc-drawer--modal');
        activeList = mdc.list.MDCList.attachTo(list);
        activeList.wrapFocus = true;
        drawerButton.hide();
    }
};

let resizeTimeout;
const resizeThrottler = () => {
    if (!resizeTimeout) {
        resizeTimeout = setTimeout(() => {
            resizeTimeout = null;
            actualResizeHandler();
        }, 66);
    }
};

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
let snackbar = null;
function snackattack(message) {
    if (!snackbar) snackbar = new mdc.snackbar.MDCSnackbar(document.querySelector('.mdc-snackbar'));
    snackbar.show({ message: message, actionText: 'OK', actionHandler: function () {} });
}

const camelToTitle = (camelCase) => camelCase
    .replace(/([A-Z])/g, (match) => ` ${match.toLowerCase()}`);

const formatDate = (obj, field) => {
    if (obj[field] != null) {
        let date = new Date(obj[field]);
        obj[`${field}-fmt`] = dateFormatter.format(date);
    }
};

function loadPayments(i, key, total=0, forwards=true) {
    const template = $('#payment-template').html();
    console.log(`loadPayments(${i},${key},${forwards})`);
    let url = (i === 1)
        ? '/payments/history'                     // first page
        : forwards
            ? `/payments/historyBefore?id=${key}` // next page right
            : `/payments/historyAfter?id=${key}`; // next page left

    $.ajax({
        url: url,
        type: 'GET',
        success: function(data) {
            let ps = data.payments;
            total = (i === 1) ? data.total : total;
            console.log(`head=${ps[0].id}, last=${ps[ps.length - 1].id} diff=${ps[0].id - ps[ps.length -1].id} (forwards=${forwards})`);
            for (let i = 0; i < ps.length; i++) {
                formatDate(ps[i], "submitted");
                formatDate(ps[i], "scheduled");
                ps[i].status_icon = (ps[i].status==="succeeded") ? "fa-check-square" : "fa-exclamation-triangle";
                ps[i].status_icon_class = (ps[i].status==="succeeded") ? "has-text-success" : "has-text-warning";
                ps[i].from_short = ps[i].from.substring(0, 4) + "…" + ps[i].from.substring(50);
                ps[i].to_short = ps[i].to.substring(0, 4) + "…" + ps[i].to.substring(50);
                if (ps[i].status === "failed" && ps[i].result != null) {
                    ps[i].status = camelToTitle(ps[i].result);
                }
            }
            const rendered = Mustache.render(template, {
                start: Math.min(i, total),
                end: i + ps.length - 1,
                total: total,
                cursor_left: (i > 1) ? "" : "cursor-default",
                cursor_right: (i <= total - ps.length) ? "" : "cursor-default",
                disabled_left: (i > 1) ? "" : "disabled",
                disabled_right: (i <= total - ps.length) ? "" : "disabled",
                payments: ps
            });
            $('#payments-list').html(rendered);
            if (i > 1) $('#history-page-left').click(function() {
                loadPayments(Math.max(1, i - 100), ps[0].id, total,false);
            });
            if (i + ps.length < total) $('#history-page-right').click(function(){
                loadPayments(i + ps.length, ps[ps.length - 1].id, total, true);
            });
        },
        error: function(xhr) {
            console.log("xhr: ", xhr);
        }
    });
}

function loadPaymentSchedule(i, key, total=0, forwards=true) {
    const template = $('#payment-schedule-template').html();

    let url = (i === 1)
        ? '/payments/scheduled'                      // first page
        : forwards
            ? `/payments/scheduledAfter?id=${key}`   // next page right
            : `/payments/scheduledBefore?id=${key}`; // next page left

    $.ajax({
        url: url,
        type: 'GET',
        success: function(data) {
            console.log(data);
            total = (i === 1) ? data.total : total;
            console.log(total);
            let ps = data.payments;
            for (let i = 0; i < ps.length; i++) {
                formatDate(ps[i], "submitted");
                formatDate(ps[i], "scheduled");
                ps[i].from_short = ps[i].from.substring(0, 4) + "…" + ps[i].from.substring(50);
                ps[i].to_short = ps[i].to.substring(0, 4) + "…" + ps[i].to.substring(50);
            }
            const rendered = Mustache.render(template, {
                start: Math.min(i, total),
                end: i + ps.length - 1,
                total: total,
                cursor_left: (i > 1) ? "" : "cursor-default",
                cursor_right: (i <= total - ps.length) ? "" : "cursor-default",
                disabled_left: (i > 1) ? "" : "disabled",
                disabled_right: (i <= total - ps.length) ? "" : "disabled",
                payments: ps
            });
            $('#payments-schedule-list').html(rendered);
            if (i > 1) $('#schedule-page-left').click(function() {
                loadPaymentSchedule(Math.max(1, i - 100), ps[0].id, total, false);
            });
            if (i + ps.length < total) $('#schedule-page-right').click(function(){
                loadPaymentSchedule(i + ps.length, ps[ps.length - 1].id, total, true);
            });
        },
        error: function(xhr) {
            console.log("xhr: ", xhr);
        }
    });
}

function loadPaymentSchedule_old() {
    const template = $('#payment-schedule-template').html();
    $.ajax({
        url: '/payments/schedule',
        type: 'GET',
        success: function(data) {
            for (let i = 0; i < data.length; i++) {
                formatDate(data[i], "submitted");
                formatDate(data[i], "scheduled");
                data[i].from_short = data[i].from.substring(0, 4) + "…" + data[i].from.substring(50);
                data[i].to_short = data[i].to.substring(0, 4) + "…" + data[i].to.substring(50);
            }
            const rendered = Mustache.render(template, { payments: data });
            $('#payments-schedule-list').html(rendered);
        },
        error: function(xhr) {
            console.log("xhr: ", xhr);
        }
    });
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
    if (section === "payments-history") loadPayments(1, 0, 0,true);
    if (section === "payments-schedule") loadPaymentSchedule(1, 0, false);
}

function hashChanged(e) {
    switchDashboardFocusTo(window.location.hash.substr(1));
}

let originalOnload = window.onload;
window.onload = function() {
    if (originalOnload) {
        originalOnload();
    }
    $(window).resize(resizeThrottler);
    actualResizeHandler();
    // topAppBar();
    // drawer();
    enableFileDragAndDrop();
    if ($('#login-form').length === 0) {
        window.onhashchange = hashChanged;
        window.location.hash = "#payments-history";
        hashChanged();
    }
};