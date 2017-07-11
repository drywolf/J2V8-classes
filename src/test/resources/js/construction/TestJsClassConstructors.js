
var JavaBaseClass = ClassHelpers.getClass('io.js.J2V8Classes.js.construction.JavaBaseClass');

var JsExtenderClass = JavaBaseClass.$extend({
    __name__: 'JsExtenderClass',

    __init__: function(a, b, c) {
        this.$super(a + c, a - b);
        this.C = c;
    },

    getC: function() {
        return this.C;
    }
});

// print('JsExtenderClass= ', typeof JsExtenderClass, ' ', JsExtenderClass);

global.base_1 = new JavaBaseClass(1, 2);
global.ext_1 = new JsExtenderClass(1, 2, 3);

class A
{
    //name = null;

    constructor(name)
    {
        this.name = name;
    }
}

class B extends A
{
    constructor(name)
    {
        super(name);
    }
}

let b = new B("bebe");
global.b = b;
