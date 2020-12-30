/** home */
Page({
    /**
    * 页面数据
    */
    data: {
        list: [],
    },

    /**
    * 页面加载时触发。一个页面只会调用一次，可以在 onLoad 的参数中获取打开当前页面路径中的参数。
    */
    onLoad(e) {
        wx.setNavigationBarTitle({
            title: 'Python系列丛书'
        }); 
        wx.showLoading({});
        this.doRequest(true);  
    },

    doRequest(isOnload) {
        let that = this;
        wx.request({
            url: 'http://47.107.46.220:10808/query', //'https://douban.uieee.com/v2/book/search?q=python', 
            data: {},
            header: {},
            method: 'get',
            success: function (response) {
                that.setData({
                    list: response.body.books
                });
                wx.showToast({
                    title: '加载成功'
                });
            },
            fail: function (error) {
                console.log('request error:' + JSON.stringify(error));
                wx.showToast({
                    title: '加载失败'
                });
            },
            complete: function () {
                console.log('request complete');
                if (isOnload) {
                    wx.hideLoading();
                } else {
                    wx.stopPullDownRefresh();
                }
            } 
        });
    },

    onItemClick(e) {
        var item = this.data.list[e.target.dataset.index];  
        wx.navigateTo({
            url: "detail?item=" + JSON.stringify(item)
        });
    },   

    onPullDownRefresh() { 
        console.log("onPullDownRefresh");
        this.doRequest(false);
    },

    /**
    * 页面卸载时触发。如cc.redirectTo或cc.navigateBack到其他页面时。
    */
    onUnload() {

    }
});