// dashboard.js - 看板页面（陌生观众看板 + 足迹留印 + 足迹还原）
window.currentPageId = 'dashboard';

$(function() {
    initDashboardTabs();
    initFootprintFileManagement();
    initFootprintReplayControls();

    // 注册保存回调，支持足迹留印开关的持久化
    registerPageSave('dashboard', function(set) {
        set.is_footprint_record = $(".is_footprint_record").is(':checked');
    });
});

// ===== 标签页切换 =====
function initDashboardTabs() {
    // 根据 URL 参数激活对应标签页
    var params = new URLSearchParams(window.location.search);
    var tabParam = params.get('tab');
    if (tabParam === 'footprint-record') {
        $('#tab-btn-footprint-record').tab('show');
    } else if (tabParam === 'footprint-replay') {
        $('#tab-btn-footprint-replay').tab('show');
    }

    // 标签页切换时更新 URL
    $('button[data-bs-toggle="tab"]').on('shown.bs.tab', function(e) {
        var tabId = $(e.target).attr('data-bs-target').replace('#tab-', '');
        var url = new URL(window.location);
        if (tabId === 'stranger-board') {
            url.searchParams.delete('tab');
        } else {
            url.searchParams.set('tab', tabId);
        }
        window.history.replaceState({}, '', url);
    });

    // 切换到足迹留印标签页时自动刷新文件列表
    $('#tab-btn-footprint-record').on('shown.bs.tab', refreshFootprintFiles);
    // 切换到足迹还原标签页时自动刷新文件下拉框
    $('#tab-btn-footprint-replay').on('shown.bs.tab', refreshFootprintFileSelect);
}

// ===== 足迹留印：文件管理 =====
function initFootprintFileManagement() {
    $('#fp-refresh-files').on('click', refreshFootprintFiles);
}

function refreshFootprintFiles() {
    $.ajax({
        url: '../listFootprintFiles',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                var files = data.result;
                var tbody = $('#fp-file-tbody').empty();
                if (files && files.length > 0) {
                    $('#fp-file-table').show();
                    $('#fp-no-files').hide();
                    files.forEach(function(f) {
                        var sizeKB = (f.size / 1024).toFixed(1);
                        tbody.append(
                            '<tr>' +
                            '<td>' + f.fileName + '</td>' +
                            '<td>' + sizeKB + ' KB</td>' +
                            '<td>' +
                            '<button class="btn btn-sm btn-outline-primary fp-download-btn" data-path="' + f.filePath + '">下载</button> ' +
                            '<button class="btn btn-sm btn-outline-danger fp-delete-btn" data-path="' + f.filePath + '">删除</button>' +
                            '</td>' +
                            '</tr>');
                    });
                } else {
                    $('#fp-file-table').hide();
                    $('#fp-no-files').show();
                }
                // 绑定按钮事件
                $('.fp-download-btn').off('click').on('click', function() {
                    window.open('../downloadFootprintFile?filePath=' + encodeURIComponent($(this).data('path')));
                });
                $('.fp-delete-btn').off('click').on('click', function() {
                    var btn = $(this);
                    if (confirm('确认删除此文件？')) {
                        $.ajax({
                            url: '../deleteFootprintFile',
                            type: 'POST',
                            data: { filePath: btn.data('path') },
                            dataType: 'json',
                            success: function() {
                                refreshFootprintFiles();
                                refreshFootprintFileSelect();
                            }
                        });
                    }
                });
            }
        }
    });
}

// ===== 足迹还原：导入与控制 =====
var replayPollInterval = null;
var currentReplayFile = null;

function initFootprintReplayControls() {
    // 上传按钮
    $('#fpr-upload-btn').on('click', function() {
        $('#fpr-upload-input').click();
    });
    $('#fpr-upload-input').on('change', function() {
        var file = this.files[0];
        if (!file) return;
        var formData = new FormData();
        formData.append('file', file);
        $.ajax({
            url: '../uploadFootprintFile',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            dataType: 'json',
            success: function(data) {
                if (data.code == '200') {
                    showMessage('文件上传成功', 'success');
                    refreshFootprintFileSelect();
                    $('#fpr-file-select').val(data.result.filePath);
                } else {
                    showMessage('上传失败', 'danger');
                }
            }
        });
    });

    // 加载文件
    $('#fpr-load-btn').on('click', function() {
        var filePath = $('#fpr-file-select').val();
        if (!filePath) {
            showMessage('请先选择文件', 'warning');
            return;
        }
        currentReplayFile = filePath;
        $('#fpr-controls').show();
        $('#fpr-status-text').text('文件已加载，等待开始');
        $('#fpr-count-text').text('');
        $('#fpr-current-text').text('');
        $('#fpr-progress-bar').css('width', '0%').text('0%');
    });

    // 重放控制按钮
    $('#fpr-start-btn').on('click', startReplay);
    $('#fpr-pause-btn').on('click', pauseReplay);
    $('#fpr-resume-btn').on('click', resumeReplay);
    $('#fpr-stop-btn').on('click', stopReplay);

    // 速度模式切换
    $('input[name="fpr-speed-mode"]').on('change', function() {
        var mode = $(this).val();
        if (mode === 'time') {
            $('#fpr-speed-time-panel').show();
            $('#fpr-speed-fixed-panel').hide();
        } else {
            $('#fpr-speed-time-panel').hide();
            $('#fpr-speed-fixed-panel').show();
        }
        updateReplaySpeed();
    });

    // 倍数播放滑块
    $('#fpr-speed-slider').on('input', function() {
        var speed = parseFloat($(this).val());
        $('#fpr-speed-label').text(speed.toFixed(1) + 'x');
        updateReplaySpeed();
    });

    // 定速播放输入
    $('#fpr-fixed-rate').on('change', function() {
        updateReplaySpeed();
    });
}

function refreshFootprintFileSelect() {
    $.ajax({
        url: '../listFootprintFiles',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                var select = $('#fpr-file-select').empty();
                select.append('<option value="">-- 选择已有文件 --</option>');
                (data.result || []).forEach(function(f) {
                    select.append('<option value="' + f.filePath + '">' + f.fileName + '</option>');
                });
            }
        }
    });
}

function updateReplaySpeed() {
    var mode = $('input[name="fpr-speed-mode"]:checked').val() || 'time';
    var value;
    if (mode === 'fixed') {
        value = parseFloat($('#fpr-fixed-rate').val()) || 1.0;
    } else {
        value = parseFloat($('#fpr-speed-slider').val()) || 1.0;
    }
    $.ajax({
        url: '../setFootprintReplaySpeed',
        type: 'POST',
        data: { speedMode: mode, speedValue: value },
        dataType: 'json'
    });
}

function startReplay() {
    if (!currentReplayFile) {
        showMessage('请先加载文件', 'warning');
        return;
    }
    var mode = $('input[name="fpr-speed-mode"]:checked').val() || 'time';
    var value;
    if (mode === 'fixed') {
        value = parseFloat($('#fpr-fixed-rate').val()) || 1.0;
    } else {
        value = parseFloat($('#fpr-speed-slider').val()) || 1.0;
    }
    $.ajax({
        url: '../startFootprintReplay',
        type: 'POST',
        data: { filePath: currentReplayFile, speedMode: mode, speedValue: value },
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                $('#fpr-status-text').text('回放中...');
                $('#fpr-count-text').text('共 ' + data.result.total + ' 条记录');
                startReplayPolling();
            } else if (data.code == '1') {
                showMessage('文件不存在', 'danger');
            } else if (data.code == '2') {
                showMessage('文件为空', 'warning');
            } else {
                showMessage('启动回放失败', 'danger');
            }
        }
    });
}

function pauseReplay() {
    $.ajax({
        url: '../pauseFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() { $('#fpr-status-text').text('已暂停'); }
    });
}

function resumeReplay() {
    $.ajax({
        url: '../resumeFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() { $('#fpr-status-text').text('回放中...'); }
    });
}

function stopReplay() {
    $.ajax({
        url: '../stopFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() {
            stopReplayPolling();
            $('#fpr-status-text').text('已停止');
            $('#fpr-progress-bar').css('width', '0%').text('0%');
            $('#fpr-current-text').text('');
        }
    });
}

function startReplayPolling() {
    stopReplayPolling();
    replayPollInterval = setInterval(function() {
        $.ajax({
            url: '../getFootprintReplayStatus',
            type: 'GET',
            dataType: 'json',
            success: function(data) {
                if (data.code == '200') {
                    var s = data.result;
                    if (!s.running || s.stopped) {
                        stopReplayPolling();
                        var pct = s.totalCount > 0 ? 100 : 0;
                        $('#fpr-progress-bar').css('width', pct + '%').text(pct + '%');
                        $('#fpr-status-text').text('回放完成');
                        return;
                    }
                    var pct = s.totalCount > 0 ? Math.round((s.currentIndex + 1) / s.totalCount * 100) : 0;
                    $('#fpr-progress-bar').css('width', pct + '%').text(pct + '%');
                    $('#fpr-current-text').text('当前: [' + (s.currentIndex + 1) + '/' + s.totalCount + '] ' + s.currentUname);
                    if (s.paused) {
                        $('#fpr-status-text').text('已暂停');
                    } else {
                        var modeLabel = s.speedMode === 'fixed' ? s.speedValue.toFixed(1) + ' 人/秒' : s.speedValue.toFixed(1) + 'x';
                        $('#fpr-status-text').text('回放中... (' + modeLabel + ')');
                    }
                }
            }
        });
    }, 500);
}

function stopReplayPolling() {
    if (replayPollInterval) {
        clearInterval(replayPollInterval);
        replayPollInterval = null;
    }
}
