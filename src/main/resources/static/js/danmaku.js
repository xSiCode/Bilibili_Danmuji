// danmaku.js - 弹幕页面（自动回复姬已移至互动姬）
window.currentPageId = 'danmaku';

registerPageSave('danmaku', function(set) {
    // 话术姬数据由 saveDanmakuStoreList 单独处理
});

$(function() {
    initPageTabs();
});
