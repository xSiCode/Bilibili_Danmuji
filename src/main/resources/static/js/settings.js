// settings.js - 基本设置页面
window.currentPageId = 'settings';

registerPageSave('settings', function(set) {
    // 通用设置
    set.is_auto = $(".is_autoStart").is(':checked');
    set.win_auto_openSet = $(".win_auto_openSet").is(':checked');
    set.auto_save_set = $(".auto_save_set").is(':checked');
    set.log = $(".is_log").is(':checked');
    set.is_watcher_log = $(".is_watcher_log").is(':checked');
    set.connect_docket = $(".connect-docket").val();
    set.avatar_dir = $(".avatar-dir").val().trim();

    // 显示设置 - 弹幕
    set.is_barrage = $(".is_barrage").is(':checked');
    set.is_barrage_guard = $(".is_barrage_guard").is(':checked');
    set.is_barrage_vip = $(".is_barrage_vip").is(':checked');
    set.is_barrage_manager = $(".is_barrage_manager").is(':checked');
    set.is_barrage_medal = $(".is_barrage_medal").is(':checked');
    set.is_barrage_ul = $(".is_barrage_ul").is(':checked');
    set.is_barrage_anchor_shield = $(".is_barrage_anchor_shield").is(':checked');

    // 显示设置 - 礼物
    set.is_gift = $(".is_gift").is(':checked');
    set.is_gift_free = $(".is_gift_free").is(':checked');

    // 显示设置 - 其他
    set.is_block = $(".is_block").is(':checked');
    set.is_cmd = $(".is_cmd").is(':checked');
    set.is_welcome_ye = $(".is_welcome").is(':checked');
    set.is_welcome_all = $(".is_welcome_all").is(':checked');
    set.is_follow_dm = $(".is_follow").is(':checked');
});

// 页面初始化
$(function() {
    initPageTabs();
});
