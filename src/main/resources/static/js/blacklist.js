// blacklist.js - 小黑屋页面
window.currentPageId = 'blacklist';

registerPageSave('blacklist', function(set) {
    // 黑名单
    if (!set.black) set.black = { names: [], uids: [] };
    set.black.all = $(".is_black_all").is(':checked');
    set.black.thank_gift = $(".is_black_gift").is(':checked');
    set.black.thank_welcome = $(".is_black_welcome").is(':checked');
    set.black.thank_follow = $(".is_black_follow").is(':checked');
    set.black.auto_reply = $(".is_black_reply").is(':checked');
    set.black.is_dynamic = $(".is_black_dynamic").is(':checked');
    set.black.names = ($(".black_names").val() || '').split('\n').map(function(s) { return s.trim(); }).filter(function(s) { return s !== ''; });
    set.black.uids = ($(".black_uids").val() || '').split('\n').map(function(s) { return s.trim(); }).filter(function(s) { return s !== ''; });

    // 白名单
    if (!set.white) set.white = {};
    set.white.all = $(".is_white_all").is(':checked');
    set.white.thank_gift = $(".is_white_gift").is(':checked');
    set.white.thank_welcome = $(".is_white_welcome").is(':checked');
    set.white.thank_follow = $(".is_white_follow").is(':checked');
    set.white.auto_reply = $(".is_white_reply").is(':checked');
    set.white.is_dynamic = $(".is_white_dynamic").is(':checked');
    set.white.names = ($(".white_names").val() || '').split('\n').map(function(s) { return s.trim(); }).filter(function(s) { return s !== ''; });
    set.white.uids = ($(".white_uids").val() || '').split('\n').map(function(s) { return s.trim(); }).filter(function(s) { return s !== ''; });

    // 自动拉黑
    if (!set.auto_block) set.auto_block = {};
    set.auto_block.is_auto_block = $(".is_auto_block").is(':checked');
    set.auto_block.block_score = parseInt($(".auto-block-score").val()) || -1;
    set.auto_block.block_interval = parseInt($(".auto-block-interval").val()) || 3;

    // badlist数据在全局badListData中，由原始saveSet传递
    if (!set.bad_list) set.bad_list = { bad_users: [] };
    if (typeof badListData !== 'undefined') {
        set.bad_list.bad_users = badListData;
    }
});

$(function() {
    initPageTabs();
});
