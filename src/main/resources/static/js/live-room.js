// live-room.js - 直播间页面
window.currentPageId = 'live-room';

registerPageSave('live-room', function(set) {
    // 状态姬
    if (!set.live_status) set.live_status = {};
    set.live_status.is_live_open = $(".livestatus_live_open").is(':checked');
    set.live_status.live_text = $(".livestatus_live_text").val();
    set.live_status.is_preparing_open = $(".livestatus_preparing_open").is(':checked');
    set.live_status.preparing_text = $(".livestatus_preparing_text").val();
    set.live_status.is_warning_open = $(".livestatus_warning_open").is(':checked');
    set.live_status.warning_text = $(".livestatus_warning_text").val();
    set.live_status.is_cut_off_open = $(".livestatus_cut_off_open").is(':checked');
    set.live_status.cut_off_text = $(".livestatus_cut_off_text").val();
    set.live_status.is_room_lock_open = $(".livestatus_room_lock_open").is(':checked');
    set.live_status.room_lock_text = $(".livestatus_room_lock_text").val();

    // 定时姬
    if (!set.timer) set.timer = {};
    set.timer.is_open = $(".timer_is_open").is(':checked');
    set.timer.timerSets = [];
    if ($("#timer-ul li").length > 0) {
        var timerSet = {};
        $("#timer-ul li").each(function (i, v) {
            timerSet.is_open = $(this).find(".timer-row-open").is(':checked');
            timerSet.time = $(this).find(".timer-row-time").val();
            timerSet.text = $(this).find(".timer-text").val();
            set.timer.timerSets.push(timerSet);
            timerSet = {};
        });
        set.timer.timerSets.sort(function(a, b) {
            return (a.time || "00:00").localeCompare(b.time || "00:00");
        });
    }

    // 广告姬
    if (!set.advert) set.advert = {};
    set.advert.is_open = $(".advert_is_open").is(':checked');
    set.advert.is_live_open = $(".advert_is_live_open").is(':checked');
    set.advert.status = Number($(".advert_status").find("option:selected").val()) - 1;
    set.advert.time = Number($(".advert_time").val());
    set.advert.time2 = Number($(".advert_time2").val());
    set.advert.adverts = $(".advert_adverts").val();
});

$(function() {
    initPageTabs();
});
