// audience.js - 观众页面
window.currentPageId = 'audience';

registerPageSave('audience', function(set) {
    // 欢迎凝视姬
    if (!set.gaze_welcome) set.gaze_welcome = {};
    set.gaze_welcome.is_open = $(".gazeWelcome_is_open").is(':checked');
    set.gaze_welcome.gazeWelcomeSets = [];
    if ($("#gazeWelcome-ul li").length > 0) {
        var gazeSet = {};
        $("#gazeWelcome-ul li").each(function (i, v) {
            gazeSet.is_open = $(this).find(".gazeWelcome-row-open").is(':checked');
            gazeSet.username = $(this).find(".gazeWelcome-username").val();
            gazeSet.text = $(this).find(".gazeWelcome-text").val();
            set.gaze_welcome.gazeWelcomeSets.push(gazeSet);
            gazeSet = {};
        });
    }

    // 欢迎姬
    if (!set.welcome) set.welcome = {};
    set.welcome.is_open = $(".welcome_is_open").is(':checked');
    set.welcome.is_open_self = $(".welcome_is_open_self").is(':checked');
    set.welcome.is_live_open = $(".welcome_is_live_open").is(':checked');
    set.welcome.is_tx_shield = $(".welcome_tx_shield").is(':checked');
    set.welcome.is_rd_shield = $(".welcome_rd_shield").is(':checked');
    set.welcome.num = Number($(".welcome_num").val());
    set.welcome.welcomes = $(".welcome_welcomes").val();
    set.welcome.delaytime = Number($(".thankwelcome_delaytime").val());
    set.welcome.list_people_shield_status = Number($(".welcome_list_people_shield_status").find("option:selected").val()) - 1;

    // 关注姬
    if (!set.follow) set.follow = {};
    set.follow.is_open = $(".follow_is_open").is(':checked');
    set.follow.is_live_open = $(".follow_is_live_open").is(':checked');
    set.follow.is_tx_shield = $(".follow_tx_shield").is(':checked');
    set.follow.is_rd_shield = $(".follow_rd_shield").is(':checked');
    set.follow.num = Number($(".follow_num").val());
    set.follow.follows = $(".follow_follows").val();
    set.follow.delaytime = Number($(".thankfollow_delaytime").val());

    // 礼物姬
    if (!set.thank_gift) set.thank_gift = { giftStrings: [], thankGiftRuleSets: [], codeStrings: [] };
    set.thank_gift.is_open = $(".thankgift_is_open").is(':checked');
    set.thank_gift.is_live_open = $(".thankgift_is_live_open").is(':checked');
    set.thank_gift.is_open_self = $(".thankgift_is_open_self").is(':checked');
    set.thank_gift.is_tx_shield = $(".thankgift_is_tx_shield").is(':checked');
    set.thank_gift.is_num = $(".thankgift_is_num").is(':checked');
    set.thank_gift.shield_status = Number($(".thankgift_shield_status").find("option:selected").val()) - 1;
    set.thank_gift.list_gift_shield_status = Number($(".thankgift_list_gift_shield_status").find("option:selected").val()) - 1;
    set.thank_gift.list_people_shield_status = Number($(".thankgift_list_people_shield_status").find("option:selected").val()) - 1;
    set.thank_gift.giftStrings = method.giftStrings_handle(set.thank_gift.giftStrings, $(".thankgift_shield").val());
    set.thank_gift.thankGiftRuleSets = [];
    if ($(".shieldgifts-tbody tr").length > 0) {
        var thankGiftRuleSet = {};
        $(".shieldgifts-tbody tr").each(function (i, v) {
            thankGiftRuleSet.is_open = $(".shieldgifts_open").eq(i).is(':checked');
            thankGiftRuleSet.gift_name = $(".shieldgifts_name").eq(i).val();
            thankGiftRuleSet.status = Number($(".shieldgifts_status").eq(i).find("option:selected").val()) - 1;
            thankGiftRuleSet.num = Number($(".shieldgifts_num").eq(i).val());
            set.thank_gift.thankGiftRuleSets.push(thankGiftRuleSet);
            thankGiftRuleSet = {};
        });
    }
    set.thank_gift.thank_status = Number($(".thankgift_thank_status").find("option:selected").val()) - 1;
    set.thank_gift.num = Number($(".thankgift_num").val());
    set.thank_gift.delaytime = Number($(".thankgift_delaytime").val());
    set.thank_gift.thank = $(".thankgift_thank").val();
    set.thank_gift.is_guard_report = $(".thankgift_is_guard_report").is(':checked');
    set.thank_gift.is_guard_local = $(".thankgift_is_guard_local").is(':checked');
    set.thank_gift.is_gift_code = $(".thankgift_is_gift_code").is(':checked');
    set.thank_gift.codeStrings = method.codeStrings_handle(set.thank_gift.codeStrings, $(".thankgift_codeStrings").val());
    set.thank_gift.report = $(".thankgift_report").val();
    set.thank_gift.report_barrage = $(".thankgift_barrageReport").val();

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
