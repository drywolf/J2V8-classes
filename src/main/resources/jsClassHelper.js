Class = abitbol.Class;

(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        // AMD. Register as an anonymous module.
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        // Node. Does not work with strict CommonJS, but
        // only CommonJS-like environments that support module.exports,
        // like Node.
        module.exports = factory();
    } else {
        // Browser globals (root is window)
        root.ClassHelpers = factory();
   }
}(this, function () {

  var _FROM_JAVA = '__EXISTING_INSTANCE';
  var _IS_SUPER = '__IS_SUPER';
  var _INTERNAL_EXTEND = '__INTERNAL_EXTEND';
  var _CLASS_INITIALIZING = '__CLASS_INITIALIZING';

  var classMap = {};
  var instanceMap = {};

  var fixValueObject = function(res) {
    if (!res || !res.hasOwnProperty('v') || !res.v) {
      return undefined;
    }
    res = res.v;

    // Check for arrays
    // if (res.__javaClass.lastIndexOf('[]') == res.__javaClass.length - 2) {
    // }
    if (Array.isArray(res)) {
      log('handleIncommingGet: array, ensuring values are backed by classes if needed ', res.length);
      for (var i = 0, j = res.length; i < j; i++) {
        res[i] = handleIncommingGet(res[i]);
      }
    }
    else if (res.__javaInstance !== undefined) {
      log('handleIncommingGet: class instance: (', res.__javaClass, ':', res.__javaInstance, ')');
      var existingInstance = instanceMap[res.__javaInstance];
      if (existingInstance) {
        log('handleIncommingGet: already exists');
        return existingInstance;
      }
      if (isInitializing(res.__javaClass)) {
        return null;
      }
      // make the instance
      var clz = getClass(res.__javaClass);

      if (!clz)
      {
        log('WARNING: Class-Info not found for class: ', res.__javaClass);
        return null;
      }

      var inst = new clz(_FROM_JAVA, res);
      return inst;
    }

    return res;
  };

  var handleIncommingGet = function(res) {
    log('handleIncommingGet: ', Object.keys(res));
    return fixValueObject(res);
  };


  var addMethod = function(inst, name, method) {
    log('adding method: ', name, ' ', typeof method, ' ', method);
    inst[name] = function() {
      log('running method: ', name);
      // log('> keys: ', Object.keys(this));
      var javaRes = method.apply(this, arguments);
      return handleIncommingGet(javaRes);
    };
  };

  var addField = function(inst, name, get, set) {
    log('adding field: ', name);
    if (!get || !set) {
      log('WARNING: fields must have both a getter and a setter');
      return;
    }

    Object.defineProperty(inst, name, {
      enumerable: true,
      // configurable: true,
      get: function() {
        log('getting(op): ', name, ' (', this.__javaInstance || this.__javaClass, ')');
        return handleIncommingGet(get.call(this));
      },
      // get: get,
      set: function(v) {
        log('setting(op): ', name, ' (', this.__javaInstance || this.__javaClass, ')');
        set.call(this, v);
      }
    });
  };


  var addProxies = function(instance, javaData, opts) {
    opts = opts || {};

    if (!javaData) {
      log('no proxy-data, no changes to instance');
      return instance;
    }

    // Add all the js -> java methods
    var methods = javaData.methods;
    if (methods && opts.methods !== false) {
      log('adding js->java methods count: ' + Object.keys(methods).length);
      log('adding js->java methods: ' + Object.keys(methods));

      for (var k in methods) {
        addMethod(instance, k, methods[k]);
      }
    }
    else {
      log('no js->java methods or disabled: ' + (opts.methods === false));
    }

    // Add getters and setters for the fields
    var fields = javaData.fields;
    var _GET_PREFIX = '__get_';
    var _SET_PREFIX = '__set_';
    if (fields && opts.fields !== false) {
      log('adding js->java fields count: ' + Object.keys(fields).length);
      log('adding js->java fields: ' + Object.keys(fields));
      for (var k in fields) {

        log('adding field: ' + k);

        if (k.indexOf(_GET_PREFIX) !== 0) {
          continue;
        }

        var strippedK = k.substring(_GET_PREFIX.length);
        addField(
          instance,
          strippedK,
          fields[k],
          fields[_SET_PREFIX + strippedK]
        );
      }
    }
    else {
      log('no js->java fields or disabled: ' + (opts.fields === false));
    }

    return instance;
  };


  var _classMixins = {};

  var registerMixins = function(className, mixins) {
    if (_classMixins[className]) {
      log('WARNING: Mixin collision for ', className);
    }
    _classMixins[className] = mixins;
  };

  var getMixins = function(className) {
    return _classMixins[className];
  };


  var funcSafeMerge = function(origObj, newObj) {
    if (!origObj) {
      return newObj;
    }
    if (!newObj) {
      return origObj;
    }

    var origT = typeof(origObj);
    var newT = typeof(newObj);
    if (origT !== newT) {
      log('WARNING: Cannot merge, mismatch types: ', origT, ' !== ', newT);
      return origT;
    }
    if (origT === 'function') {
      return function() {
        log('running merged function: ', this.$class.$name, ' ', Object.keys(this));
        origObj.apply(this, arguments);
        return newObj.apply(this, arguments);
      };
    }
    if (Array.isArray(origObj)) {
      return origObj.concat(newObj);
    }
    if (origT === 'object') {
      var res = {};
      for (var k in newObj) {
        if (origObj[k]) {
          res[k] = funcSafeMerge(origObj[k], newObj[k]);
        } else {
          res[k] = newObj[k];
        }
      }
      return res;
    }
    // if (origT === 'string') {
    //   return newObj;
    // }
    // if (origT === 'number') {
    //   return newObj;
    // }
    // log('WARNING: Cannot merge, unknown type ', origT);
    return newObj;
  };


  var addJavaFieldsToObj = function(obj, fromClass) {
    if (!fromClass) {
      fromClass = obj.__javaClass;
    }
    if (!obj.__javaInstance) {
      log('WARNING: Trying to addJavaFields from ', fromClass, ' to an instance not backed by java');
      return;
    }

    log('addJavaFieldsToObj: from ', fromClass, ' to ', obj.__javaInstance);
    var classInfo = getClassInfo(fromClass);
    var superClass = classInfo.__javaSuperclass;
    if (superClass) {
      log('addJavaFieldsToObj: add superclazz ', superClass);
      addJavaFieldsToObj(obj, superClass);
    }

    log('addJavaFieldsToObj: Adding proxies for ', fromClass);
    addProxies(obj, classInfo.publics);
  };


  var addJavaFields = function(fromClass) {
    if (!this.__javaInstance) {
      log('WARNING: Trying to addJavaFields from ', fromClass, ' to an instance not backed by java');
      return;
    }

    log('addJavaFields: from ', fromClass, ' to ', this.__javaInstance);
    var classInfo = getClassInfo(fromClass);
    var superClass = classInfo.__javaSuperclass;
    if (superClass) {
      addJavaFields.call(this, superClass);
    }

    log('addJavaFields: Adding proxies for ', fromClass);
    // log('TEST: ', Object.keys(classInfo));
    addProxies(this, classInfo.publics, { methods: false });
  };


  var isInitializing = function(className) {
    return classMap[className] === _CLASS_INITIALIZING;
  };
  var isDynamicClass = function(className) {
    return className.indexOf(_DYNAMIC_PACKAGE) === 0;
  };


  var _classInfoCache = {};

  var getClassInfo = function(className) {
    var existing = _classInfoCache[className];
    if (existing) {
      return existing;
    }

    // Make the classInfo js side, so that it isnt released
    var classInfo = getBlankClassInfo();
    JavaGetClass(className, classInfo);
    _classInfoCache[className] = classInfo;
    return classInfo;
  };


  var getClass = function(className) {
    //log("BEGIN getClass " + className);

    if (!className) {
      throw new Error('Must provide className to getClass');
    }

    var existing = classMap[className];
    if (existing) {
      if (isInitializing(className)) {
        log('WARNING: Calling getClass() from inside getClass() stack (class is already initializing)');
        return null;
      }
      return existing;
    }
    classMap[className] = _CLASS_INITIALIZING;
    log('getting class data for: ', className);
    var classInfo = getClassInfo(className);

    if (!classInfo.__javaClass) {
      log('WARNING: Class not found: ', className);
      return null;
    }

    var internalClassInit = function() {
      log('Running internalClassInit for ', className);
      var instChildClass = this.$class.__javaClass;

      if (isDynamicClass(className)) {
        // Wait to call createInstance until we have js args for js -> java super
        log('WARNING: internalClassInit called with dynamic class ', className);
        //return;
      }

      var instData;
      var isSuper = arguments[0] === _IS_SUPER;
      if (isSuper || arguments[0] === _FROM_JAVA) {
        log('adopting java instance: ', className, (isSuper ? ' (for super)' : ''));
        instData = arguments[1];

        if (!instData) {
          log('WARNING: instData not valid... adopting java instance must have failed');
          throw new Error('adopt java instance failed');
        }
      } else {
        log('creating new instance: ', instChildClass, ' (non dynamic super ', className, ')');
        var args = Array.prototype.slice.call(arguments);
        args.unshift(instChildClass);
        log('> Instance args: ', JSON.stringify(args));
        instData = JavaCreateInstance.apply(this, args);

        if (!instData) {
          log('WARNING: instData not valid... JavaCreateInstance must have failed');
          throw new Error('JavaCreateInstance failed');
        }
      }


      if (this.__internalClassInitCalled) {
        return;
      }
      this.__internalClassInitCalled = true;


      if (!instData.__javaInstance) {
        log('WARNING: No instData.__javaInstance');
      } else {
        this.__javaInstance = instData.__javaInstance;

        //if (isDynamicClass(className)) {
          log('adding __class property for class: ' + className);
          this.__class = instData.__class;
        //}

        log('(inst: ' + instChildClass + ' : ' + this.__javaInstance + ')');
      }

      var existing = instanceMap[this.__javaInstance];
      if (!isSuper) {
        if (instanceMap[this.__javaInstance]) {
          // TODO: this is fired for super classes
          log('WARNING: instanceMap collision. Instance already in map: ' + existing.$class.__javaClass + ':' + existing.__javaInstance);
        } else {
          instanceMap[this.__javaInstance] = this;
        }
      }

      // Manually back in the field properties (abitbol handles the functions...?)
      addJavaFields.call(this, instChildClass);
    };

    var classConstructor = {
      __name__: classInfo.__javaClass.substring(classInfo.__javaClass.lastIndexOf('.') + 1)
    };

    if (!isDynamicClass(className)) {
      log("Applying default ctor for " + className);
      classConstructor.__init__ = function() {
        log("Calling " + className + ".__init__ #1 > ", JSON.stringify(arguments));
        internalClassInit.apply(this, arguments);
      };
    }

    var superClz = null;
    var superClzName = classInfo.__javaSuperclass;
    if (superClzName && superClzName !== 'java.lang.Object') {
      log('Getting super (', className, ' extends ', superClzName, ')');
      superClz = getClass(superClzName);
    }

    var jsMixins = getMixins(className);
    if (classInfo.publics) {
      log('Adding publics: ', JSON.stringify(classInfo.publics));
      // Exclude proxies for any JS methods
      // var javaPublics = classInfo.publics.slice();
      // if (jsMixins) {
      //   for (var k in jsMixins) {
      //     var i = javaPublics.indexOf(k);
      //     if (i >= 0) {
      //       log('> Skipping public (defined in js): ', k);
      //       javaPublics.splice(i, 1);
      //     }
      //   }
      // }

      var jsDefined = jsMixins || {};
      var excludeMixins = function(target) {
        var res = {};
        for (var k in target) {
          // if (k.indexOf('__') === 0) {
          //   javaPublics[k] = classInfo.publics[k];
          //   continue;
          // }
          log('> Checking: ', k);
          if (jsDefined[k]) {
            log('> Skipping public (defined in js): ', k);
            continue;
          }
          res[k] = target[k];
        }
        return res;
      };
      var javaPublics = {
        fields: excludeMixins(classInfo.publics.fields),
        methods: excludeMixins(classInfo.publics.methods)
      };

      // addProxies(classConstructor, classInfo.publics);
      addProxies(classConstructor, javaPublics);
    }

    if (classInfo.statics) {
      log('Adding statics: ', JSON.stringify(classInfo.statics));
      classConstructor.__classvars__ = addProxies(
        {
          __javaClass: classInfo.__javaClass
        },
        classInfo.statics
      );
    }

    // Add any original js mixins if they exist
    if (jsMixins) {
      log('Adding JS mixins: ', Object.keys(jsMixins));
      Object.keys(jsMixins).forEach(function(k) {
        var v = jsMixins[k];
        var existing = classConstructor[k];
        if (k.indexOf('__') === 0) {
          classConstructor[k] = funcSafeMerge(existing, v);
        } else {
          classConstructor[k] = v;
        }
      });
    }

    // Ensure that there is an __init__
    if (!classConstructor.__init__) {
      log('No user defined init, using internalClassInit');
      classConstructor.__init__ = internalClassInit;
    }
    else {
      log('User defined init found, what should we do?');
      // var userInit = classConstructor.__init__;
      // classConstructor.__init__ = function() {
      //   log("Calling " + className + ".__init__ #2 > ", JSON.stringify(arguments));
      //   internalClassInit.apply(this, arguments);
      //   userInit.apply(this, arguments);
      // };
    }
    /*else {
      log('User defined init found, calling super');
      var userInit = classConstructor.__init__;
      classConstructor.__init__ = function() {
        internalClassInit.apply(this, arguments);
        userInit.apply(this, arguments);
      };
    }*/

    log('Generating abitbol class: ', classConstructor.__name__, ' classConstructor: ', Object.keys(classConstructor));
    // log('> TEST ', classConstructor.getSubtype);
    var clz;
    if (superClz) {
      log('> Using super.$extend for: "', superClz.$name, '" (', superClz.__javaClass, ')');
      clz = superClz.$extend(_INTERNAL_EXTEND, classConstructor);
    } else {
      log('> No super, running Class.$extend');
      log(Object.keys(abitbol));
      clz = Class.$extend(classConstructor);
    }

    clz.__javaClass = classInfo.__javaClass;
    clz.__javaSuperclass = classInfo.__javaSuperclass;

    Object.defineProperty(clz, '$extend', {
        enumerable: false,
        configurable: true,
        value: function() {
          log('Custom $extend');

          if (arguments[0] === _INTERNAL_EXTEND) {
            log('> Internal extend, running Class.$extend');
            var args = Array.prototype.slice.call(arguments, 1);
            return Class.$extend.apply(this, args);
          }

          var superClass = this.$class;
          if (!superClass) {
            log('> No superClass, running Class.$extend');
            return Class.$extend.apply(this, arguments);
          }

          if (_classUid >= _MAX_CUSTOM_CLASS_COUNT) {
            log('WARNING: max custom class count hit, falling back to js class extend');
            return Class.$extend.apply(this, arguments);
          }

          var newClassConstructor = arguments[0];
          var className = newClassConstructor .__name__;
          if (!className) {
            className = 'Dynamic_' + (_classUid++);
          }

          var javaClass = _DYNAMIC_PACKAGE + '.' + __runtimeName + '.' + className;
          log('> parent.__javaClass= ', superClass.__javaClass);
          log('> new javaClass: ', javaClass);

          registerMixins(javaClass, newClassConstructor);

          var methods = [];
          var fields = [];

          var keys = Object.keys(newClassConstructor);
          for (var i in keys) {
            var k = keys[i];
            if (k.charAt(0) === '_') {
              continue;
            }

            var v = newClassConstructor[k];
            var t = typeof(v);
            if (t === 'function') {
              methods.push({
                type: t,
                name: k,
                annotations: abitbol.extractAnnotations(v)
              });
            }
          }

          log('> Generating Java class');
          JavaGenerateClass(
            javaClass,
            superClass.__javaClass,
            // fields,
            methods
          );

          log('> Creating JS class');
          return getClass(javaClass);
        }
    });

    log('Class info load complete: ', className);
    classMap[className] = clz;

    return clz;
  };

  var _DYNAMIC_PACKAGE = 'io.js.enderScript.Dynamic';
  var _MAX_CUSTOM_CLASS_COUNT = 1000;
  var _classUid = 0;


  var getBlankClassInfo = function() {
    return {
      publics: {
        fields: {},
        methods: {}
      },
      statics: {
        fields: {},
        methods: {}
      }
    };
  };

  var createJsInstance = function(res) {
    try {
      var javaData = res && res.v;

      log('Creating JS instance...');

      if (javaData) {
        log('for: ', javaData.__javaInstance, ' class: ', javaData.__javaClass);
        log('data: ', Object.keys(javaData));
      }
      else {
        log('ERROR: no instance/class information available');
        return;
      }

      log("before fixValueObject");
      var inst = fixValueObject(res);
      log("after fixValueObject");

      if (inst) {
        instanceMap[javaData.__javaInstance] = inst;
        log("added instance to map: " + javaData.__javaInstance);
      }
      else
        log("Was unable to create JS instance for Java object");
    }
    catch (e) {
      log("Error in JS-Ctor: " + e + "(type " + e.stack + ")");
    }
  };

  var executeInstanceMethod = function(javaInstance, methodName, args) {
    log('Calling js instance method: ', javaInstance, ' ', methodName);
    var inst = instanceMap[javaInstance];

    if (!inst) {
      log('WARNING: No instance registered for: ', javaInstance);
      return;
    }

    var fn = inst[methodName];
    if (!fn) {
      log('WARNING: No function named: ', methodName);
      return;
    }

    args = args.v;
    log('executeInstanceMethod: Fixing args');
    for (var key in args) {
      args[key] = fixValueObject(args[key]);
    }

    // var args = Array.prototype.slice.call(arguments, 2);
    // return fn.apply(inst, args);
    return fn.apply(inst, args);
  };


  return {
    createJsInstance: createJsInstance,
    getClass: getClass,
    addJavaFieldsToObj: addJavaFieldsToObj,
    getBlankClassInfo: getBlankClassInfo,
    executeInstanceMethod: executeInstanceMethod
  };

}));

createJsInstance = ClassHelpers.createJsInstance;
executeInstanceMethod = ClassHelpers.executeInstanceMethod;
