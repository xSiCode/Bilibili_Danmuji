// blacklist.js - 小黑屋页面
window.currentPageId = 'blacklist';

registerPageSave('blacklist', function(set) {
    // 自动拉黑
    if (!set.auto_block) set.auto_block = {};
    set.auto_block.is_auto_block = $(".is_auto_block").is(':checked');
    set.auto_block.block_score = parseInt($(".auto-block-score").val()) || -1;
    set.auto_block.block_interval = parseInt($(".auto-block-interval").val()) || 120;

    // 关键词检测姬
    method.kwSyncToSet(set);
});

$(function() {
    initPageTabs();
});
