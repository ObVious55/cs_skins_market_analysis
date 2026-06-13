// 全局变量
let currentItemData = null;
let currentItemId = null;
let currentSalesData = null;
let searchResults = [];
let salesChartInstance = null;

// DOM元素
const authSection = document.getElementById('auth-section');
const unauthenticated = document.getElementById('unauthenticated');
const authenticated = document.getElementById('authenticated');
const userAvatar = document.getElementById('userAvatar');
const userName = document.getElementById('userName');
const itemSearch = document.getElementById('itemSearch');
const searchBtn = document.getElementById('searchBtn');
const itemSelector = document.getElementById('itemSelector');
const itemDropdown = document.getElementById('itemDropdown');
const selectItemBtn = document.getElementById('selectItemBtn');
const priceDataCard = document.getElementById('priceDataCard');
const predictionCard = document.getElementById('predictionCard');
const currentPrice = document.getElementById('currentPrice');
const priceChange = document.getElementById('priceChange');
const predictBtn = document.getElementById('predictBtn');
const loadingSpinner = document.getElementById('loadingSpinner');
const predictionResult = document.getElementById('predictionResult');
const hotItems = document.getElementById('hotItems');
const salesPlatformSelector = document.getElementById('salesPlatformSelector');
const salesChartContainer = document.getElementById('salesChartContainer');

// 🌟 新增：持仓分析按钮和预测标题
const analyzeHoldingsBtn = document.getElementById('analyzeHoldingsBtn');
const predictionTitle = document.getElementById('predictionTitle');
const clearChatBtn = document.getElementById('clearChatBtn');
const loadingText = document.getElementById('loadingText');

// 初始化页面
document.addEventListener('DOMContentLoaded', function() {
    // 配置 marked 库以更好地处理 Markdown
    marked.setOptions({
        breaks: true,           // 支持 GitHub 风格的换行
        gfm: true,              // 启用 GitHub Flavored Markdown
        headerIds: false,       // 不为标题添加 ID
        mangle: false,          // 不转义电子邮件
        sanitize: false,        // 不清理 HTML
        smartLists: true,       // 使用更智能的列表行为
        smartypants: true,      // 使用更智能的标点符号
        langPrefix: 'language-' // 代码块语言前缀
    });

    // --- 新增：登录状态检测逻辑 ---
    checkLoginStatus();

    loadHotItems();

    // 原有绑定保持不变...
    searchBtn.addEventListener('click', handleSearch);
    selectItemBtn.addEventListener('click', handleItemSelect);
    predictBtn.addEventListener('click', handlePredict);
    salesPlatformSelector.addEventListener('change', handlePlatformChange);

    itemSearch.addEventListener('keypress', (e) => { if (e.key === 'Enter') handleSearch(); });
    itemDropdown.addEventListener('change', () => { selectItemBtn.disabled = !itemDropdown.value; });

    // 🌟 新增：持仓分析按钮事件
    if (analyzeHoldingsBtn) {
        analyzeHoldingsBtn.addEventListener('click', handleAnalyzeHoldings);
    }
    
    // 🌟 新增：清空对话按钮
    if (clearChatBtn) {
        clearChatBtn.addEventListener('click', function() {
            document.getElementById('chatHistory').innerHTML = '';
            predictionResult.innerHTML = '';
            predictionResult.style.display = 'none';
            document.getElementById('followUpInputArea').style.display = 'none';
            predictBtn.textContent = isHoldingsAnalysisMode ? '🔮 重新分析持仓' : '开始预测';
            clearChatBtn.style.display = 'none';
        });
    }
});

// 处理平台选择变化
function handlePlatformChange() {
    if (currentItemId) {
        loadSalesData(currentItemId);
    }
}

// 搜索功能
async function handleSearch() {
    const searchTerm = itemSearch.value.trim();
    if (!searchTerm) return alert('请输入饰品名称或ID');

    try {
        searchBtn.disabled = true;
        searchBtn.textContent = '🔍 搜索中...';
        const response = await fetch(`/api/search-item?query=${encodeURIComponent(searchTerm)}`);
        const searchData = await response.json();
        if (!searchData.success) throw new Error(searchData.error || '搜索失败');
        if (searchData.data && searchData.data.length > 0) {
            searchResults = searchData.data;
            displayItemSelector(searchResults);
        } else {
            alert('未找到相关饰品');
            hideItemSelector();
        }
    } catch (error) {
        console.error('搜索错误:', error);
        alert('搜索失败: ' + error.message);
        hideItemSelector();
    } finally {
        searchBtn.disabled = false;
        searchBtn.textContent = '🔍 搜索';
    }
}

// 显示饰品选择器
function displayItemSelector(items) {
    itemDropdown.innerHTML = '<option value="">请选择具体饰品...</option>';
    items.forEach((item) => {
        const option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.name;
        itemDropdown.appendChild(option);
    });
    itemSelector.style.display = 'block';
    priceDataCard.style.display = 'none';
    predictionCard.style.display = 'none';
    salesChartContainer.style.display = 'none';
    selectItemBtn.disabled = true;
    itemSelector.scrollIntoView({ behavior: 'smooth' });
}

// 隐藏饰品选择器
function hideItemSelector() {
    itemSelector.style.display = 'none';
}

// 处理饰品选择
async function handleItemSelect() {
    const selectedId = itemDropdown.value;
    if (!selectedId) return alert('请选择一个饰品');

    try {
        selectItemBtn.disabled = true;
        selectItemBtn.textContent = '📊 加载中...';
        currentItemId = selectedId;
        await Promise.all([
            loadPriceData(selectedId),
            loadSalesData(selectedId)
        ]);
    } catch (error) {
        console.error('加载饰品数据错误:', error);
        alert('加载失败: ' + error.message);
    } finally {
        selectItemBtn.disabled = false;
        selectItemBtn.textContent = '📊 查看价格';
    }
}

// 加载价格数据
async function loadPriceData(itemId) {
    try {
        const response = await fetch(`/api/price-data/${itemId}`);
        const data = await response.json();
        if (!data.success) throw new Error(data.error);
        currentItemData = data.data;
        displayPriceData(currentItemData);
    } catch (error) {
        console.error('加载价格数据错误:', error);
        alert('加载价格数据失败: ' + error.message);
    }
}

// 加载销售和成交量数据
async function loadSalesData(itemId) {
    try {
        const selectedPlatform = salesPlatformSelector.value;
        const response = await fetch(`/api/sales-data/${itemId}?platform=${selectedPlatform}`);
        const data = await response.json();
        if (!data.success) {
             throw new Error(data.error || '获取销售数据时发生未知错误');
        }
        currentSalesData = data;
        if (data.sell_num_data || data.turnover_data || data.price_data) {
            displaySalesChart(data);
        } else {
            salesChartContainer.style.display = 'none';
        }
    } catch(error) {
        console.error('获取销售数据失败:', error.message);
        salesChartContainer.style.display = 'none';
    }
}

// 显示价格数据
function displayPriceData(data) {
    if (data && data.data && data.data.goods_info) {
        const itemInfo = data.data.goods_info;
        currentPrice.textContent = `¥${(itemInfo.buff_sell_price || 0).toLocaleString()}`;
        const change = itemInfo.sell_price_rate_7 || 0;
        priceChange.textContent = `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;
        priceChange.className = `change ${change >= 0 ? 'positive' : 'negative'}`;
        updatePriceChart(itemInfo);
        priceDataCard.style.display = 'block';
        predictionCard.style.display = 'block';
        predictionResult.style.display = 'none';
        priceDataCard.scrollIntoView({ behavior: 'smooth' });
    }
}

// 显示销售与成交量图表
function displaySalesChart(data) {
    if (salesChartInstance) {
        salesChartInstance.dispose();
    }
    salesChartContainer.style.display = 'block';
    salesChartInstance = echarts.init(document.getElementById('salesChart'));

    const legendData = [];
    const series = [];
    const allData = {};
    const selectedPlatformText = salesPlatformSelector.options[salesPlatformSelector.selectedIndex].text;
    
    // ... (图表数据处理逻辑保持不变)
    if (data.sell_num_data && data.sell_num_data.timestamp) {
        legendData.push(`在售数量 (${selectedPlatformText})`);
        data.sell_num_data.timestamp.forEach((ts, index) => {
            const date = new Date(ts).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
            if (!allData[date]) allData[date] = {};
            allData[date].sellNum = data.sell_num_data.main_data[index];
        });
    }
    if (data.turnover_data && data.turnover_data.timestamp) {
        legendData.push('成交量 (Steam)');
        data.turnover_data.timestamp.forEach((ts, index) => {
            const date = new Date(ts).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
            if (!allData[date]) allData[date] = {};
            allData[date].turnover = data.turnover_data.main_data[index];
        });
    }
    if (data.price_data && data.price_data.timestamp) {
        legendData.push(`价格 (${selectedPlatformText})`);
        data.price_data.timestamp.forEach((ts, index) => {
            const date = new Date(ts).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
            if (!allData[date]) allData[date] = {};
            allData[date].price = data.price_data.main_data[index];
        });
    }

    const dates = Object.keys(allData).sort((a, b) => new Date(a) - new Date(b));
    const sellNumSeriesData = dates.map(date => allData[date].sellNum || null);
    const turnoverSeriesData = dates.map(date => allData[date].turnover || null);
    const priceSeriesData = dates.map(date => allData[date].price || null);

    if (legendData.includes(`在售数量 (${selectedPlatformText})`)) {
        series.push({ name: `在售数量 (${selectedPlatformText})`, type: 'line', smooth: true, yAxisIndex: 0, data: sellNumSeriesData });
    }
    if (legendData.includes('成交量 (Steam)')) {
        series.push({ name: '成交量 (Steam)', type: 'bar', yAxisIndex: 0, data: turnoverSeriesData });
    }
    if (legendData.includes(`价格 (${selectedPlatformText})`)) {
        series.push({ name: `价格 (${selectedPlatformText})`, type: 'line', smooth: true, yAxisIndex: 1, data: priceSeriesData });
    }
    
    if (series.length === 0) {
        salesChartContainer.style.display = 'none';
        return;
    }

    const option = {
        tooltip: { trigger: 'axis' },
        legend: { data: legendData },
        grid: { top: '20%', left: '15%', right: '15%', bottom: '30%' },
        xAxis: { type: 'category', data: dates, axisLabel: { hideOverlap: true } },
        yAxis: [{ type: 'value', name: '数量' }, { type: 'value', name: '价格 (¥)' }],
        dataZoom: [{ type: 'slider', start: 50, end: 100 }],
        series: series
    };

    salesChartInstance.setOption(option);
    new ResizeObserver(() => salesChartInstance.resize()).observe(salesChartContainer);
}


// 更新价格图表
function updatePriceChart(itemInfo) {
    const priceChart = document.getElementById('priceChart');
    priceChart.innerHTML = `
        <div class="price-trend">
            <h4>价格趋势</h4>
            <div class="trend-item"><span>1天:</span><span class="${itemInfo.sell_price_rate_1 >= 0 ? 'positive' : 'negative'}">${itemInfo.sell_price_rate_1 > 0 ? '+' : ''}${(itemInfo.sell_price_rate_1 || 0).toFixed(2)}%</span></div>
            <div class="trend-item"><span>7天:</span><span class="${itemInfo.sell_price_rate_7 >= 0 ? 'positive' : 'negative'}">${itemInfo.sell_price_rate_7 > 0 ? '+' : ''}${(itemInfo.sell_price_rate_7 || 0).toFixed(2)}%</span></div>
            <div class="trend-item"><span>30天:</span><span class="${itemInfo.sell_price_rate_30 >= 0 ? 'positive' : 'negative'}">${itemInfo.sell_price_rate_30 > 0 ? '+' : ''}${(itemInfo.sell_price_rate_30 || 0).toFixed(2)}%</span></div>
        </div>
        <div class="platform-prices">
            <h4>平台价格</h4>
            <div class="platform-item"><span>BUFF:</span><span>¥${(itemInfo.buff_sell_price || 0).toLocaleString()}</span></div>
            <div class="platform-item"><span>悠悠有品:</span><span>¥${(itemInfo.yyyp_sell_price || 0).toLocaleString()}</span></div>
            <div class="platform-item"><span>Steam:</span><span>¥${(itemInfo.steam_sell_price || 0).toLocaleString()}</span></div>
        </div>
    `;
}

// 处理价格预测 - 改为调用 Agent
async function handlePredict() {
    if (!currentItemData) return alert('请先搜索并选择饰品');

    const steamUserId = localStorage.getItem('steam_user_id');
    const itemName = itemDropdown.options[itemDropdown.selectedIndex].text;
    const isFollowUp = predictBtn.textContent === '🔮 继续追问';

    if (isFollowUp) {
        document.getElementById('followUpInputArea').style.display = 'block';
        document.getElementById('userQuestion').focus();
        return;
    }

    try {
        predictBtn.disabled = true;
        predictBtn.textContent = '🔮 预测中...';
        loadingSpinner.style.display = 'block';
        loadingText.textContent = 'AI 正在分析饰品数据...';
        predictionTitle.textContent = 'AI 单品分析';
        predictBtn.style.display = 'inline-block';

        // 🌟 关键：直接把 itemId 和 itemName 告诉 Agent，让它不用搜索
        const userMessage = `请帮我分析这个饰品：${itemName}（ID: ${currentItemId}），给出价格走势分析和投资建议`;

        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message: userMessage,
                steamId: steamUserId || '',
                followUp: false
            })
        });

        if (!response.ok) {
            throw new Error('预测失败: ' + response.status);
        }

        // 🌟 关键修改：解析 JSON 响应而不是纯文本
        const result = await response.json();
        
        // 检查返回的状态码
        if (result.code === 200) {
            // 成功：提取 data 字段中的 Markdown 内容
            const aiResponse = result.data;
            
            // 使用 marked 库解析 Markdown 为 HTML
            predictionResult.innerHTML = marked.parse(aiResponse);
            predictionResult.style.display = 'block';

            predictBtn.textContent = '🔮 继续追问';
            predictBtn.disabled = false;
            document.getElementById('followUpInputArea').style.display = 'block';
            document.getElementById('userQuestion').placeholder = '你可以继续追问，例如：建议现在买入吗？';
            document.getElementById('chatHistory').innerHTML = '';
        } else {
            // 失败：显示错误信息
            throw new Error(result.message || '预测失败');
        }

    } catch (error) {
        alert('预测失败: ' + error.message);
    } finally {
        loadingSpinner.style.display = 'none';
    }
}

//  追问逻辑代码
async function sendFollowUp() {
    const questionInput = document.getElementById('userQuestion');
    const question = questionInput.value.trim();
    if (!question) return;

    const steamUserId = localStorage.getItem('steam_user_id');
    const chatHistory = document.getElementById('chatHistory');

    try {
        // 界面反馈：添加用户的问题到对话区
        chatHistory.innerHTML += `<div class="user-q"><strong>问：</strong>${question}</div>`;
        questionInput.value = ''; // 清空输入框

        // 🌟 根据模式选择不同的请求参数
        let requestBody;

        if (isHoldingsAnalysisMode) {
            requestBody = {
                message: question,
                steamId: steamUserId || ''
            };
        } else {
            const itemName = itemDropdown.options[itemDropdown.selectedIndex].text;

            requestBody = {
                steamId: steamUserId || '',
                message: `用户追问：${question}`,
                followUp: true,
            };
        }

        // 发送追问请求
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            throw new Error('请求失败: ' + response.status);
        }

        // 🌟 关键修改：解析 JSON 响应
        const result = await response.json();
        
        if (result.code === 200) {
            // 成功：提取 data 字段
            const aiResponse = result.data;
            
            // 界面反馈：添加 AI 的回答，使用 marked 解析 Markdown
            chatHistory.innerHTML += `<div class="ai-a"><strong>AI：</strong>${marked.parse(aiResponse)}</div>`;

            // 滚动到底部
            chatHistory.scrollTop = chatHistory.scrollHeight;
        } else {
            // 失败：显示错误信息
            throw new Error(result.message || '请求失败');
        }

    } catch (error) {
        console.error('追问失败:', error);
        alert('追问失败: ' + error.message);
    }
}

// 绑定发送按钮事件
document.getElementById('sendQuestionBtn').addEventListener('click', sendFollowUp);
document.getElementById('userQuestion').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') sendFollowUp();
});

// 加载热门饰品
async function loadHotItems() {
    try {
        const response = await fetch('/api/hot-items');
        const data = await response.json();
        if (data.success && data.data) displayHotItems(data.data);
    } catch (error) {
        console.error('加载热门饰品失败:', error);
    }
}

// 显示热门饰品
function displayHotItems(items) {
    hotItems.innerHTML = '';
    items.slice(0, 10).forEach(item => {
        const itemCard = document.createElement('div');
        itemCard.className = 'item-card';
        const avgChange = (item.sell_price_7 || 0);
        const changeClass = avgChange >= 0 ? 'positive' : 'negative';

        itemCard.innerHTML = `
            <h4>${item.name}</h4>
            <p>系列总值: ¥${(item.total_value || 0).toLocaleString()}</p>
            <small class="${changeClass}">7日涨跌: ${avgChange >= 0 ? '+' : ''}${avgChange.toFixed(2)}%</small>
        `;

        itemCard.addEventListener('click', () => {
            itemSearch.value = item.name;
            handleSearch();
        });
        hotItems.appendChild(itemCard);
    });
}

/**
 * 检查登录状态
 */
function checkLoginStatus() {
    const urlParams = new URLSearchParams(window.location.search);
    const steamIdFromUrl = urlParams.get('steamId');
    const loginSuccess = urlParams.get('loginSuccess');

    // 场景 A：刚刚从回调地址跳回来
    if (loginSuccess === 'true' && steamIdFromUrl) {
        localStorage.setItem('steam_user_id', steamIdFromUrl);
        // 清理 URL 里的参数，让地址栏变干净 (变为 /index.html)
        window.history.replaceState({}, document.title, window.location.pathname);
        fetchUserInfo(steamIdFromUrl);
    }
    // 场景 B：之前已经登录过了，ID 保存在浏览器里
    else {
        const savedSteamId = localStorage.getItem('steam_user_id');
        if (savedSteamId) {
            fetchUserInfo(savedSteamId);
        }
    }
}

/**
 * 从后端获取 Redis 中的用户信息
 */
async function fetchUserInfo(steamId) {
    try {
        // 请求我们在 Controller 中补全的接口
        const response = await fetch(`/api/auth/user/info?steamId=${steamId}`);
        if (!response.ok) throw new Error('用户数据获取失败');

        const userData = await response.json();

        // 更新 UI 展示
        unauthenticated.style.display = 'none';
        authenticated.style.display = 'block';
        userAvatar.src = userData.avatarUrl;
        userName.textContent = userData.nickname;
    } catch (error) {
        console.error('获取用户信息失败:', error);
        // 如果获取失败（比如 Redis 缓存过期了），清除本地存储让用户重新登录
        localStorage.removeItem('steam_user_id');
    }
}

/**
 * 退出登录
 */
function logout() {
    localStorage.removeItem('steam_user_id');
    // 刷新页面回到未登录状态
    window.location.reload();
}

// 🌟 新增：持仓分析功能（支持多轮对话）
let isHoldingsAnalysisMode = false; // 标记当前是否为持仓分析模式

async function handleAnalyzeHoldings() {
    const steamId = localStorage.getItem('steam_user_id');
    
    if (!steamId) {
        alert('请先登录 Steam 账号');
        return;
    }

    try {
        // 切换到持仓分析模式
        isHoldingsAnalysisMode = true;
        
        // 更新 UI 状态
        predictionTitle.textContent = '🤖 AI 持仓分析';
        loadingText.textContent = 'AI 正在分析您的库存...';
        loadingSpinner.style.display = 'block';
        predictionResult.style.display = 'none';
        document.getElementById('chatHistory').innerHTML = '';
        document.getElementById('followUpInputArea').style.display = 'none';
        predictionCard.style.display = 'block';
        predictBtn.style.display = 'none'; // 隐藏"开始预测"按钮
        clearChatBtn.style.display = 'block'; // 显示"清空对话"按钮
        
        // 滚动到预测区域
        predictionCard.scrollIntoView({ behavior: 'smooth' });

        // 调用 AI Agent 接口 - 首次分析
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message: '请帮我分析一下我的持仓情况，给出投资建议',
                steamId: steamId
            })
        });

        if (!response.ok) {
            throw new Error('分析失败: ' + response.status);
        }

        // 🌟 关键修改：解析 JSON 响应
        const result = await response.json();
        
        if (result.code === 200) {
            // 成功：提取 data 字段
            const aiResponse = result.data;
            
            // 显示 AI 的回答，使用 marked 解析 Markdown
            loadingSpinner.style.display = 'none';
            predictionResult.innerHTML = marked.parse(aiResponse);
            predictionResult.style.display = 'block';
            
            // 🌟 关键：显示追问输入框
            document.getElementById('followUpInputArea').style.display = 'block';
            document.getElementById('userQuestion').placeholder = '继续提问，例如：哪些饰品该卖出？';
            document.getElementById('userQuestion').focus();
        } else {
            // 失败：显示错误信息
            throw new Error(result.message || '分析失败');
        }

    } catch (error) {
        console.error('持仓分析失败:', error);
        loadingSpinner.style.display = 'none';
        alert('持仓分析失败: ' + error.message);
    }
}

// 当页面加载完成后执行
document.addEventListener('DOMContentLoaded', () => {
    const syncBtn = document.getElementById('syncInventoryBtn');

    if (syncBtn) {
        syncBtn.addEventListener('click', async function() {


            const currentSteamId = localStorage.getItem('steam_user_id');

            if (!currentSteamId) {
                alert("⚠️ 未获取到您的账户信息，请先登录！");
                return; // 直接终止运行，不发请求
            }

            // 防止重复点击，提供视觉反馈
            syncBtn.disabled = true;
            const originalText = syncBtn.innerText; // 记住按钮原本的文字
            syncBtn.classList.add('loading-state');
            syncBtn.innerText = "🚀 正在同步库存...";

            try {
                const response = await fetch(`/api/inventory/refresh?steamId=${encodeURIComponent(currentSteamId)}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });

                if (response.ok) {
                    // response.text() 会拿到后端返回的 "库存同步成功！"
                    const successMsg = await response.text();
                    alert(`✅ ${successMsg}`);

                    // 动态局部刷新库存
                    if (typeof loadUserInventory === 'function') {
                        loadUserInventory();
                    } else {
                        window.location.reload();
                    }
                }
                else if (response.status === 401) {
                    alert("❌ 登录已失效，请重新登录 Steam。");
                }

                else if (response.status === 403) {
                    const errorMsg = await response.text();
                    alert("🔒 " + errorMsg); // 提示：您的库存未公开...
                }
                else {
                    const errorMsg = await response.text();
                    alert("⚠️ 同步失败: " + (errorMsg || "服务器内部错误"));
                }
            } catch (err) {
                console.error("Network Error:", err);
                alert("🌐 网络连接失败，请检查后端服务是否启动。");
            } finally {
                // 无论成功失败，恢复按钮状态
                syncBtn.disabled = false;
                syncBtn.classList.remove('loading-state');
                syncBtn.innerText = originalText;
            }
        });
    }
});