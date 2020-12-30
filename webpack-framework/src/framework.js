
global.guid = function() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        let r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
};

global.judgeIsNotNull = function(pageId, id, val) {
    return !!(pageId && id && val);

};

global.getExpValue = function (data, script) {
    const expFunc = exp => {
        return new Function('', 'with(this){' + exp + '}').bind(data)();
    };
    let value = expFunc(script);
    if (value instanceof Object) {
        return JSON.stringify(value);
    }
    if (value instanceof Array) {
        return JSON.stringify(value);
    }
    return value;
};

require('./page');