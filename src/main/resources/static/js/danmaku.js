// danmaku.js - 弹幕页面
window.currentPageId = 'danmaku';

registerPageSave('danmaku', function(set) {
    // 自动回复姬
    if (!set.reply) set.reply = { autoReplySets: [] };
    set.reply.is_open = $(".replys_is_open").is(':checked');
    set.reply.is_live_open = $(".replys_is_live_open").is(':checked');
    set.reply.is_open_self = $(".replys_is_open_self").is(':checked');
    set.reply.time = Number($(".replys_time").val());
    set.reply.list_people_shield_status = Number($(".replys_list_people_shield_status").find("option:selected").val()) - 1;
    set.reply.autoReplySets = [];
    if ($(".replys-ul li").length > 0) {
        var autoReplySet = {};
        $(".replys-ul li").each(function (i, v) {
            autoReplySet.is_open = $(".reply_open").eq(i).is(':checked');
            autoReplySet.is_accurate = $(".reply_oc").eq(i).is(':checked');
            var keywords = [];
            var shields = [];
            var keyword = $(".reply_keywords").eq(i).val();
            var shield = $(".reply_shields").eq(i).val();
            var reply = $(".reply_rs").eq(i).val();
            if (keyword === null) keyword = "";
            autoReplySet.keywords = method.giftStrings_handle(keywords, keyword);
            autoReplySet.shields = method.giftStrings_handle(shields, shield);
            autoReplySet.reply = reply;
            set.reply.autoReplySets.push(autoReplySet);
            autoReplySet = {};
        });
    }
});

$(function() {
    initPageTabs();
});
