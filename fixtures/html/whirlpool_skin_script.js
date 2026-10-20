/*! jQuery v4.0.0 | (c) OpenJS Foundation and other contributors | jquery.com/license */
!function(e,t){"use strict";"object"==typeof module&&"object"==typeof module.exports?module.exports=t(e,!0):t(e)}("undefined"!=typeof window?window:this,function(e,t){"use strict";if(!e.document)throw Error("jQuery requires a window with a document");var n=[],r=Object.getPrototypeOf,i=n.slice,o=n.flat?function(e){return n.flat.call(e)}:function(e){return n.concat.apply([],e)},a=n.push,s=n.indexOf,u={},l=u.toString,c=u.hasOwnProperty,f=c.toString,p=f.call(Object),d={};function h(e){return null==e?e+"":"object"==typeof e?u[l.call(e)]||"object":typeof e}function g(e){return null!=e&&e===e.window}function v(e){var t=!!e&&e.length,n=h(e);return!("function"==typeof e||g(e))&&("array"===n||0===t||"number"==typeof t&&t>0&&t-1 in e)}var y=e.document,m={type:!0,src:!0,nonce:!0,noModule:!0};function x(e,t,n){var r,i=(n=n||y).createElement("script");for(r in i.text=e,m)t&&t[r]&&(i[r]=t[r]);n.head.appendChild(i).parentNode&&i.parentNode.removeChild(i)}var b="4.0.0",w=/HTML$/i,T=function(e,t){return new T.fn.init(e,t)};function C(e,t){return e.nodeName&&e.nodeName.toLowerCase()===t.toLowerCase()}T.fn=T.prototype={jquery:b,constructor:T,length:0,toArray:function(){return i.call(this)},get:function(e){return null==e?i.call(this):e<0?this[e+this.length]:this[e]},pushStack:function(e){var t=T.merge(this.constructor(),e);return t.prevObject=this,t},each:function(e){return T.each(this,e)},map:function(e){return this.pushStack(T.map(this,function(t,n){return e.call(t,n,t)}))},slice:function(){return this.pushStack(i.apply(this,arguments))},first:function(){return this.eq(0)},last:function(){return this.eq(-1)},even:function(){return this.pushStack(T.grep(this,function(e,t){return(t+1)%2}))},odd:function(){return this.pushStack(T.grep(this,function(e,t){return t%2}))},eq:function(e){var t=this.length,n=+e+(e<0?t:0);return this.pushStack(n>=0&&n<t?[this[n]]:[])},end:function(){return this.prevObject||this.constructor()}},T.extend=T.fn.extend=function(){var e,t,n,r,i,o,a=arguments[0]||{},s=1,u=arguments.length,l=!1;for("boolean"==typeof a&&(l=a,a=arguments[s]||{},s++),"object"!=typeof a&&"function"!=typeof a&&(a={}),s===u&&(a=this,s--);s<u;s++)if(null!=(e=arguments[s]))for(t in e)r=e[t],"__proto__"!==t&&a!==r&&(l&&r&&(T.isPlainObject(r)||(i=Array.isArray(r)))?(n=a[t],o=i&&!Array.isArray(n)?[]:i||T.isPlainObject(n)?n:{},i=!1,a[t]=T.extend(l,o,r)):void 0!==r&&(a[t]=r));return a},T.extend({expando:"jQuery"+(b+Math.random()).replace(/\D/g,""),isReady:!0,error:function(e){throw Error(e)},noop:function(){},isPlainObject:function(e){var t,n;return!!e&&"[object Object]"===l.call(e)&&(!(t=r(e))||"function"==typeof(n=c.call(t,"constructor")&&t.constructor)&&f.call(n)===p)},isEmptyObject:function(e){var t;for(t in e)return!1;return!0},globalEval:function(e,t,n){x(e,{nonce:t&&t.nonce},n)},each:function(e,t){var n,r=0;if(v(e))for(n=e.length;r<n&&!1!==t.call(e[r],r,e[r]);r++);else for(r in e)if(!1===t.call(e[r],r,e[r]))break;return e},text:function(e){var t,n="",r=0,i=e.nodeType;if(!i)while(t=e[r++])n+=T.text(t);return 1===i||11===i?e.textContent:9===i?e.documentElement.textContent:3===i||4===i?e.nodeValue:n},makeArray:function(e,t){var n=t||[];return null!=e&&(v(Object(e))?T.merge(n,"string"==typeof e?[e]:e):a.call(n,e)),n},inArray:function(e,t,n){return null==t?-1:s.call(t,e,n)},isXMLDoc:function(e){var t=e&&e.namespaceURI,n=e&&(e.ownerDocument||e).documentElement;return!w.test(t||n&&n.nodeName||"HTML")},contains:function(e,t){var n=t&&t.parentNode;return e===n||!!(n&&1===n.nodeType&&(e.contains?e.contains(n):e.compareDocumentPosition&&16&e.compareDocumentPosition(n)))},merge:function(e,t){for(var n=+t.length,r=0,i=e.length;r<n;r++)e[i++]=t[r];return e.length=i,e},grep:function(e,t,n){for(var r=[],i=0,o=e.length,a=!n;i<o;i++)!t(e[i],i)!==a&&r.push(e[i]);return r},map:function(e,t,n){var r,i,a=0,s=[];if(v(e))for(r=e.length;a<r;a++)null!=(i=t(e[a],a,n))&&s.push(i);else for(a in e)null!=(i=t(e[a],a,n))&&s.push(i);return o(s)},guid:1,support:d}),"function"==typeof Symbol&&(T.fn[Symbol.iterator]=n[Symbol.iterator]),T.each("Boolean Number String Function Array Date RegExp Object Error Symbol".split(" "),function(e,t){u["[object "+t+"]"]=t.toLowerCase()});var j=n.pop,E="[\\x20\\t\\r\\n\\f]",k=y.documentMode,S=k&&RegExp(":enabled|:disabled|\\["+E+"*name"+E+"*="+E+"*(?:''|\"\")"),D=RegExp("^"+E+"+|((?:^|[^\\\\])(?:\\\\.)*)"+E+"+$","g"),A="(?:\\\\[\\da-fA-F]{1,6}"+E+"?|\\\\[^\\r\\n\\f]|[\\w-]|[^\0-\\x7f])+",N=RegExp("^"+E+"*([>+~]|"+E+")"+E+"*"),q=RegExp(E+"|>"),O=/[+~]/,L=y.documentElement,H=L.matches||L.msMatchesSelector;function P(){var e=[];function t(n,r){return e.push(n+" ")>T.expr.cacheLength&&delete t[e.shift()],t[n+" "]=r}return t}function R(e){return e&&void 0!==e.getElementsByTagName&&e}var M="\\["+E+"*("+A+")(?:"+E+"*([*^$|!~]?=)"+E+"*(?:'((?:\\\\.|[^\\\\'])*)'|\"((?:\\\\.|[^\\\\\"])*)\"|("+A+"))|)"+E+"*\\]",W=":("+A+")(?:\\((('((?:\\\\.|[^\\\\'])*)'|\"((?:\\\\.|[^\\\\\"])*)\")|((?:\\\\.|[^\\\\()[\\]]|"+M+")*)|.*)\\)|)",$={ID:RegExp("^#("+A+")"),CLASS:RegExp("^\\.("+A+")"),TAG:RegExp("^("+A+"|[*])"),ATTR:RegExp("^"+M),PSEUDO:RegExp("^"+W),CHILD:RegExp("^:(only|first|last|nth|nth-last)-(child|of-type)(?:\\("+E+"*(even|odd|(([+-]|)(\\d*)n|)"+E+"*(?:([+-]|)"+E+"*(\\d+)|))"+E+"*\\)|)","i")},I=new RegExp(W),F=RegExp("\\\\[\\da-fA-F]{1,6}"+E+"?|\\\\([^\\r\\n\\f])","g"),B=function(e,t){var n="0x"+e.slice(1)-65536;return t||(n<0?String.fromCharCode(n+65536):String.fromCharCode(n>>10|55296,1023&n|56320))};function _(e){return e.replace(F,B)}function U(e){T.error("Syntax error, unrecognized expression: "+e)}var X=RegExp("^"+E+"*,"+E+"*"),z=P();function Y(e,t){var n,r,i,o,a,s,u,l=z[e+" "];if(l)return t?0:l.slice(0);a=e,s=[],u=T.expr.preFilter;while(a){for(o in(!n||(r=X.exec(a)))&&(r&&(a=a.slice(r[0].length)||a),s.push(i=[])),n=!1,(r=N.exec(a))&&(n=r.shift(),i.push({value:n,type:r[0].replace(D," ")}),a=a.slice(n.length)),$)(r=T.expr.match[o].exec(a))&&(!u[o]||(r=u[o](r)))&&(n=r.shift(),i.push({value:n,type:o,matches:r}),a=a.slice(n.length));if(!n)break}return t?a.length:a?U(e):z(e,s).slice(0)}function G(e){for(var t=0,n=e.length,r="";t<n;t++)r+=e[t].value;return r}function V(e,t,n,r,i,o,a){var s=0,u=e.length,l=null==n;if("object"===h(n))for(s in i=!0,n)V(e,t,s,n[s],!0,o,a);else if(void 0!==r&&(i=!0,"function"!=typeof r&&(a=!0),l&&(a?(t.call(e,r),t=null):(l=t,t=function(e,t,n){return l.call(T(e),n)})),t))for(;s<u;s++)t(e[s],n,a?r:r.call(e[s],s,t(e[s],n)));return i?e:l?t.call(e):u?t(e[0],n):o}var Q=/[^\x20\t\r\n\f]+/g;T.fn.extend({attr:function(e,t){return V(this,T.attr,e,t,arguments.length>1)},removeAttr:function(e){return this.each(function(){T.removeAttr(this,e)})}}),T.extend({attr:function(e,t,n){var r,i,o=e.nodeType;if(3!==o&&8!==o&&2!==o)return void 0===e.getAttribute?T.prop(e,t,n):(1===o&&T.isXMLDoc(e)||(i=T.attrHooks[t.toLowerCase()]),void 0!==n)?null===n||!1===n&&0!==t.toLowerCase().indexOf("aria-")?void T.removeAttr(e,t):i&&"set"in i&&void 0!==(r=i.set(e,n,t))?r:(e.setAttribute(t,n),n):i&&"get"in i&&null!==(r=i.get(e,t))?r:null==(r=e.getAttribute(t))?void 0:r},attrHooks:{},removeAttr:function(e,t){var n,r=0,i=t&&t.match(Q);if(i&&1===e.nodeType)while(n=i[r++])e.removeAttribute(n)}}),k&&(T.attrHooks.type={set:function(e,t){if("radio"===t&&C(e,"input")){var n=e.value;return e.setAttribute("type",t),n&&(e.value=n),t}}});var J=/([\0-\x1f\x7f]|^-?\d)|^-$|[^\x80-\uFFFF\w-]/g;function K(e,t){return t?"\0"===e?"\uFFFD":e.slice(0,-1)+"\\"+e.charCodeAt(e.length-1).toString(16)+" ":"\\"+e}T.escapeSelector=function(e){return(e+"").replace(J,K)};var Z=n.sort,ee=n.splice;function et(e,t){if(e===t)return en=!0,0;var n=!e.compareDocumentPosition-!t.compareDocumentPosition;return n?n:1&(n=(e.ownerDocument||e)==(t.ownerDocument||t)?e.compareDocumentPosition(t):1)?e==y||e.ownerDocument==y&&T.contains(y,e)?-1:t==y||t.ownerDocument==y&&T.contains(y,t)?1:0:4&n?-1:1}T.uniqueSort=function(e){var t,n=[],r=0,i=0;if(en=!1,Z.call(e,et),en){while(t=e[i++])t===e[i]&&(r=n.push(i));while(r--)ee.call(e,n[r],1)}return e},T.fn.uniqueSort=function(){return this.pushStack(T.uniqueSort(i.apply(this)))};var en,er,ei,eo,ea,es,eu=0,el=0,ec=P(),ef=P(),ep=P(),ed=RegExp(E+"+","g"),eh=RegExp("^"+A+"$"),eg=T.extend({needsContext:RegExp("^"+E+"*[>+~]|:(even|odd|eq|gt|lt|nth|first|last)(?:\\("+E+"*((?:-\\d)?\\d*)"+E+"*\\)|)(?=[^-]|$)","i")},$),ev=/^(?:input|select|textarea|button)$/i,ey=/^h\d$/i,em=/^(?:#([\w-]+)|(\w+)|\.([\w-]+))$/,ex=function(){eE()},eb=eS(function(e){return!0===e.disabled&&C(e,"fieldset")},{dir:"parentNode",next:"legend"});function ew(e,t,n,r){var i,o,s,u,l,c,f,p=t&&t.ownerDocument,d=t?t.nodeType:9;if(n=n||[],"string"!=typeof e||!e||1!==d&&9!==d&&11!==d)return n;if(!r&&(eE(t),t=t||eo,es)){if(11!==d&&(l=em.exec(e))){if(i=l[1]){if(9===d)return(s=t.getElementById(i))&&a.call(n,s),n;else if(p&&(s=p.getElementById(i))&&T.contains(t,s))return a.call(n,s),n}else if(l[2])return a.apply(n,t.getElementsByTagName(e)),n;else if((i=l[3])&&t.getElementsByClassName)return a.apply(n,t.getElementsByClassName(i)),n}if(!ep[e+" "]&&(!S||!S.test(e))){if(f=e,p=t,1===d&&(q.test(e)||N.test(e))){((p=O.test(e)&&R(t.parentNode)||t)!=t||k)&&((u=t.getAttribute("id"))?u=T.escapeSelector(u):t.setAttribute("id",u=T.expando)),o=(c=Y(e)).length;while(o--)c[o]=(u?"#"+u:":scope")+" "+G(c[o]);f=c.join(",")}try{return a.apply(n,p.querySelectorAll(f)),n}catch(t){ep(e,!0)}finally{u===T.expando&&t.removeAttribute("id")}}}return eq(e.replace(D,"$1"),t,n,r)}function eT(e){return e[T.expando]=!0,e}function eC(e){return function(t){if("form"in t){if(t.parentNode&&!1===t.disabled){if("label"in t)if("label"in t.parentNode)return t.parentNode.disabled===e;else return t.disabled===e;return t.isDisabled===e||!e!==t.isDisabled&&eb(t)===e}return t.disabled===e}return"label"in t&&t.disabled===e}}function ej(e){return eT(function(t){return t*=1,eT(function(n,r){var i,o=e([],n.length,t),a=o.length;while(a--)n[i=o[a]]&&(n[i]=!(r[i]=n[i]))})})}function eE(e){var t,n=e?e.ownerDocument||e:y;n!=eo&&9===n.nodeType&&(ea=(eo=n).documentElement,es=!T.isXMLDoc(eo),k&&y!=eo&&(t=eo.defaultView)&&t.top!==t&&t.addEventListener("unload",ex))}for(er in ew.matches=function(e,t){return ew(e,null,null,t)},ew.matchesSelector=function(e,t){if(eE(e),es&&!ep[t+" "]&&(!S||!S.test(t)))try{return H.call(e,t)}catch(e){ep(t,!0)}return ew(t,eo,null,[e]).length>0},T.expr={cacheLength:50,createPseudo:eT,match:eg,find:{ID:function(e,t){if(void 0!==t.getElementById&&es){var n=t.getElementById(e);return n?[n]:[]}},TAG:function(e,t){return void 0!==t.getElementsByTagName?t.getElementsByTagName(e):t.querySelectorAll(e)},CLASS:function(e,t){if(void 0!==t.getElementsByClassName&&es)return t.getElementsByClassName(e)}},relative:{">":{dir:"parentNode",first:!0}," ":{dir:"parentNode"},"+":{dir:"previousSibling",first:!0},"~":{dir:"previousSibling"}},preFilter:{ATTR:function(e){return e[1]=_(e[1]),e[3]=_(e[3]||e[4]||e[5]||""),"~="===e[2]&&(e[3]=" "+e[3]+" "),e.slice(0,4)},CHILD:function(e){return e[1]=e[1].toLowerCase(),"nth"===e[1].slice(0,3)?(e[3]||U(e[0]),e[4]=+(e[4]?e[5]+(e[6]||1):2*("even"===e[3]||"odd"===e[3])),e[5]=+(e[7]+e[8]||"odd"===e[3])):e[3]&&U(e[0]),e},PSEUDO:function(e){var t,n=!e[6]&&e[2];return $.CHILD.test(e[0])?null:(e[3]?e[2]=e[4]||e[5]||"":n&&I.test(n)&&(t=Y(n,!0))&&(t=n.indexOf(")",n.length-t)-n.length)&&(e[0]=e[0].slice(0,t),e[2]=n.slice(0,t)),e.slice(0,3))}},filter:{ID:function(e){var t=_(e);return function(e){return e.getAttribute("id")===t}},TAG:function(e){var t=_(e).toLowerCase();return"*"===e?function(){return!0}:function(e){return C(e,t)}},CLASS:function(e){var t=ec[e+" "];return t||(t=RegExp("(^|"+E+")"+e+"("+E+"|$)"))&&ec(e,function(e){return t.test("string"==typeof e.className&&e.className||void 0!==e.getAttribute&&e.getAttribute("class")||"")})},ATTR:function(e,t,n){return function(r){var i=T.attr(r,e);return null==i?"!="===t:!t||((i+="","="===t)?i===n:"!="===t?i!==n:"^="===t?n&&0===i.indexOf(n):"*="===t?n&&i.indexOf(n)>-1:"$="===t?n&&i.slice(-n.length)===n:"~="===t?(" "+i.replace(ed," ")+" ").indexOf(n)>-1:"|="===t&&(i===n||i.slice(0,n.length+1)===n+"-"))}},CHILD:function(e,t,n,r,i){var o="nth"!==e.slice(0,3),a="last"!==e.slice(-4),s="of-type"===t;return 1===r&&0===i?function(e){return!!e.parentNode}:function(t,n,u){var l,c,f,p,d,h=o!==a?"nextSibling":"previousSibling",g=t.parentNode,v=s&&t.nodeName.toLowerCase(),y=!u&&!s,m=!1;if(g){if(o){while(h){f=t;while(f=f[h])if(s?C(f,v):1===f.nodeType)return!1;d=h="only"===e&&!d&&"nextSibling"}return!0}if(d=[a?g.firstChild:g.lastChild],a&&y){m=(p=(l=(c=g[T.expando]||(g[T.expando]={}))[e]||[])[0]===eu&&l[1])&&l[2],f=p&&g.childNodes[p];while(f=++p&&f&&f[h]||(m=p=0)||d.pop())if(1===f.nodeType&&++m&&f===t){c[e]=[eu,p,m];break}}else if(y&&(m=p=(l=(c=t[T.expando]||(t[T.expando]={}))[e]||[])[0]===eu&&l[1]),!1===m){while(f=++p&&f&&f[h]||(m=p=0)||d.pop())if((s?C(f,v):1===f.nodeType)&&++m&&(y&&((c=f[T.expando]||(f[T.expando]={}))[e]=[eu,m]),f===t))break}return(m-=i)===r||m%r==0&&m/r>=0}}},PSEUDO:function(e,t){var n=T.expr.pseudos[e]||T.expr.setFilters[e.toLowerCase()]||U("unsupported pseudo: "+e);return n[T.expando]?n(t):n}},pseudos:{not:eT(function(e){var t=[],n=[],r=eN(e.replace(D,"$1"));return r[T.expando]?eT(function(e,t,n,i){var o,a=r(e,null,i,[]),s=e.length;while(s--)(o=a[s])&&(e[s]=!(t[s]=o))}):function(e,i,o){return t[0]=e,r(t,null,o,n),t[0]=null,!n.pop()}}),has:eT(function(e){return function(t){return ew(e,t).length>0}}),contains:eT(function(e){return e=_(e),function(t){return(t.textContent||T.text(t)).indexOf(e)>-1}}),lang:eT(function(e){return eh.test(e||"")||U("unsupported lang: "+e),e=_(e).toLowerCase(),function(t){var n;do if(n=es?t.lang:t.getAttribute("xml:lang")||t.getAttribute("lang"))return(n=n.toLowerCase())===e||0===n.indexOf(e+"-");while((t=t.parentNode)&&1===t.nodeType);return!1}}),target:function(t){var n=e.location&&e.location.hash;return n&&n.slice(1)===t.id},root:function(e){return e===ea},focus:function(e){return e===eo.activeElement&&eo.hasFocus()&&!!(e.type||e.href||~e.tabIndex)},enabled:eC(!1),disabled:eC(!0),checked:function(e){return C(e,"input")&&!!e.checked||C(e,"option")&&!!e.selected},selected:function(e){return k&&e.parentNode&&e.parentNode.selectedIndex,!0===e.selected},empty:function(e){for(e=e.firstChild;e;e=e.nextSibling)if(e.nodeType<6)return!1;return!0},parent:function(e){return!T.expr.pseudos.empty(e)},header:function(e){return ey.test(e.nodeName)},input:function(e){return ev.test(e.nodeName)},button:function(e){return C(e,"input")&&"button"===e.type||C(e,"button")},text:function(e){return C(e,"input")&&"text"===e.type},first:ej(function(){return[0]}),last:ej(function(e,t){return[t-1]}),eq:ej(function(e,t,n){return[n<0?n+t:n]}),even:ej(function(e,t){for(var n=0;n<t;n+=2)e.push(n);return e}),odd:ej(function(e,t){for(var n=1;n<t;n+=2)e.push(n);return e}),lt:ej(function(e,t,n){var r;for(r=n<0?n+t:n>t?t:n;--r>=0;)e.push(r);return e}),gt:ej(function(e,t,n){for(var r=n<0?n+t:n;++r<t;)e.push(r);return e})}},T.expr.pseudos.nth=T.expr.pseudos.eq,{radio:!0,checkbox:!0,file:!0,password:!0,image:!0})T.expr.pseudos[er]=function(e){return function(t){return C(t,"input")&&t.type===e}}(er);for(er in{submit:!0,reset:!0})T.expr.pseudos[er]=function(e){return function(t){return(C(t,"input")||C(t,"button"))&&t.type===e}}(er);function ek(){}function eS(e,t,n){var r=t.dir,i=t.next,o=i||r,a=n&&"parentNode"===o,s=el++;return t.first?function(t,n,i){while(t=t[r])if(1===t.nodeType||a)return e(t,n,i);return!1}:function(t,n,u){var l,c,f=[eu,s];if(u){while(t=t[r])if((1===t.nodeType||a)&&e(t,n,u))return!0}else while(t=t[r])if(1===t.nodeType||a)if(c=t[T.expando]||(t[T.expando]={}),i&&C(t,i))t=t[r]||t;else{if((l=c[o])&&l[0]===eu&&l[1]===s)return f[2]=l[2];if(c[o]=f,f[2]=e(t,n,u))return!0}return!1}}function eD(e){return e.length>1?function(t,n,r){var i=e.length;while(i--)if(!e[i](t,n,r))return!1;return!0}:e[0]}function eA(e,t,n,r,i){for(var o,a=[],s=0,u=e.length,l=null!=t;s<u;s++)(o=e[s])&&(!n||n(o,r,i))&&(a.push(o),l&&t.push(s));return a}function eN(e,t){var n,r,i,o,u=[],l=[],c=ef[e+" "];if(!c){t||(t=Y(e)),o=t.length;while(o--)(c=function e(t){for(var n,r,i,o=t.length,u=T.expr.relative[t[0].type],l=u||T.expr.relative[" "],c=+!!u,f=eS(function(e){return e===n},l,!0),p=eS(function(e){return s.call(n,e)>-1},l,!0),d=[function(e,t,r){var i=!u&&(r||t!=ei)||((n=t).nodeType?f(e,t,r):p(e,t,r));return n=null,i}];c<o;c++)if(r=T.expr.relative[t[c].type])d=[eS(eD(d),r)];else{if((r=T.expr.filter[t[c].type].apply(null,t[c].matches))[T.expando]){for(i=++c;i<o&&!T.expr.relative[t[i].type];i++);return function e(t,n,r,i,o,u){return i&&!i[T.expando]&&(i=e(i)),o&&!o[T.expando]&&(o=e(o,u)),eT(function(e,u,l,c){var f,p,d,h,g=[],v=[],y=u.length,m=e||function(e,t,n){for(var r=0,i=t.length;r<i;r++)ew(e,t[r],n);return n}(n||"*",l.nodeType?[l]:l,[]),x=t&&(e||!n)?eA(m,g,t,l,c):m;if(r?r(x,h=o||(e?t:y||i)?[]:u,l,c):h=x,i){f=eA(h,v),i(f,[],l,c),p=f.length;while(p--)(d=f[p])&&(h[v[p]]=!(x[v[p]]=d))}if(e){if(o||t){if(o){f=[],p=h.length;while(p--)(d=h[p])&&f.push(x[p]=d);o(null,h=[],f,c)}p=h.length;while(p--)(d=h[p])&&(f=o?s.call(e,d):g[p])>-1&&(e[f]=!(u[f]=d))}}else h=eA(h===u?h.splice(y,h.length):h),o?o(null,u,h,c):a.apply(u,h)})}(c>1&&eD(d),c>1&&G(t.slice(0,c-1).concat({value:" "===t[c-2].type?"*":""})).replace(D,"$1"),r,c<i&&e(t.slice(c,i)),i<o&&e(t=t.slice(i)),i<o&&G(t))}d.push(r)}return eD(d)}(t[o]))[T.expando]?u.push(c):l.push(c);(c=ef(e,(n=u.length>0,r=l.length>0,i=function(e,t,i,o,s){var c,f,p,d=0,h="0",g=e&&[],v=[],y=ei,m=e||r&&T.expr.find.TAG("*",s),x=eu+=null==y?1:Math.random()||.1;for(s&&(ei=t==eo||t||s);null!=(c=m[h]);h++){if(r&&c){f=0,t||c.ownerDocument==eo||(eE(c),i=!es);while(p=l[f++])if(p(c,t||eo,i)){a.call(o,c);break}s&&(eu=x)}n&&((c=!p&&c)&&d--,e&&g.push(c))}if(d+=h,n&&h!==d){f=0;while(p=u[f++])p(g,v,t,i);if(e){if(d>0)while(h--)g[h]||v[h]||(v[h]=j.call(o));v=eA(v)}a.apply(o,v),s&&!e&&v.length>0&&d+u.length>1&&T.uniqueSort(o)}return s&&(eu=x,ei=y),g},n?eT(i):i))).selector=e}return c}function eq(e,t,n,r){var i,o,s,u,l,c="function"==typeof e&&e,f=!r&&Y(e=c.selector||e);if(n=n||[],1===f.length){if((o=f[0]=f[0].slice(0)).length>2&&"ID"===(s=o[0]).type&&9===t.nodeType&&es&&T.expr.relative[o[1].type]){if(!(t=(T.expr.find.ID(_(s.matches[0]),t)||[])[0]))return n;c&&(t=t.parentNode),e=e.slice(o.shift().value.length)}i=eg.needsContext.test(e)?0:o.length;while(i--){if(s=o[i],T.expr.relative[u=s.type])break;if((l=T.expr.find[u])&&(r=l(_(s.matches[0]),O.test(o[0].type)&&R(t.parentNode)||t))){if(o.splice(i,1),!(e=r.length&&G(o)))return a.apply(n,r),n;break}}}return(c||eN(e,f))(r,t,!es,n,!t||O.test(e)&&R(t.parentNode)||t),n}function eO(e,t,n){var r=[],i=void 0!==n;while((e=e[t])&&9!==e.nodeType)if(1===e.nodeType){if(i&&T(e).is(n))break;r.push(e)}return r}function eL(e,t){for(var n=[];e;e=e.nextSibling)1===e.nodeType&&e!==t&&n.push(e);return n}ek.prototype=T.expr.pseudos,T.expr.setFilters=new ek,eE(),T.find=ew,ew.compile=eN,ew.select=eq,ew.setDocument=eE,ew.tokenize=Y;var eH=T.expr.match.needsContext,eP=/^<([a-z][^\/\0>:\x20\t\r\n\f]*)[\x20\t\r\n\f]*\/?>(?:<\/\1>|)$/i;function eR(e){return"<"===e[0]&&">"===e[e.length-1]&&e.length>=3}function eM(e,t,n){return"function"==typeof t?T.grep(e,function(e,r){return!!t.call(e,r,e)!==n}):t.nodeType?T.grep(e,function(e){return e===t!==n}):"string"!=typeof t?T.grep(e,function(e){return s.call(t,e)>-1!==n}):T.filter(t,e,n)}T.filter=function(e,t,n){var r=t[0];return(n&&(e=":not("+e+")"),1===t.length&&1===r.nodeType)?T.find.matchesSelector(r,e)?[r]:[]:T.find.matches(e,T.grep(t,function(e){return 1===e.nodeType}))},T.fn.extend({find:function(e){var t,n,r=this.length,i=this;if("string"!=typeof e)return this.pushStack(T(e).filter(function(){for(t=0;t<r;t++)if(T.contains(i[t],this))return!0}));for(t=0,n=this.pushStack([]);t<r;t++)T.find(e,i[t],n);return r>1?T.uniqueSort(n):n},filter:function(e){return this.pushStack(eM(this,e||[],!1))},not:function(e){return this.pushStack(eM(this,e||[],!0))},is:function(e){return!!eM(this,"string"==typeof e&&eH.test(e)?T(e):e||[],!1).length}});var eW,e$=/^(?:\s*(<[\w\W]+>)[^>]*|#([\w-]+))$/;(T.fn.init=function(e,t){var n,r;if(!e)return this;if(e.nodeType)return this[0]=e,this.length=1,this;if("function"==typeof e)return void 0!==eW.ready?eW.ready(e):e(T);if(eR(n=e+""))n=[null,e,null];else{if("string"!=typeof e)return T.makeArray(e,this);n=e$.exec(e)}if(n&&(n[1]||!t))if(!n[1])return(r=y.getElementById(n[2]))&&(this[0]=r,this.length=1),this;else{if(t=t instanceof T?t[0]:t,T.merge(this,T.parseHTML(n[1],t&&t.nodeType?t.ownerDocument||t:y,!0)),eP.test(n[1])&&T.isPlainObject(t))for(n in t)"function"==typeof this[n]?this[n](t[n]):this.attr(n,t[n]);return this}return!t||t.jquery?(t||eW).find(e):this.constructor(t).find(e)}).prototype=T.fn,eW=T(y);var eI=/^(?:parents|prev(?:Until|All))/,eF={children:!0,contents:!0,next:!0,prev:!0};function eB(e,t){while((e=e[t])&&1!==e.nodeType);return e}function e_(e){return e}function eU(e){throw e}function eX(e,t,n,r){var i;try{e&&"function"==typeof(i=e.promise)?i.call(e).done(t).fail(n):e&&"function"==typeof(i=e.then)?i.call(e,t,n):t.apply(void 0,[e].slice(r))}catch(e){n(e)}}T.fn.extend({has:function(e){var t=T(e,this),n=t.length;return this.filter(function(){for(var e=0;e<n;e++)if(T.contains(this,t[e]))return!0})},closest:function(e,t){var n,r=0,i=this.length,o=[],a="string"!=typeof e&&T(e);if(!eH.test(e)){for(;r<i;r++)for(n=this[r];n&&n!==t;n=n.parentNode)if(n.nodeType<11&&(a?a.index(n)>-1:1===n.nodeType&&T.find.matchesSelector(n,e))){o.push(n);break}}return this.pushStack(o.length>1?T.uniqueSort(o):o)},index:function(e){return e?"string"==typeof e?s.call(T(e),this[0]):s.call(this,e.jquery?e[0]:e):this[0]&&this[0].parentNode?this.first().prevAll().length:-1},add:function(e,t){return this.pushStack(T.uniqueSort(T.merge(this.get(),T(e,t))))},addBack:function(e){return this.add(null==e?this.prevObject:this.prevObject.filter(e))}}),T.each({parent:function(e){var t=e.parentNode;return t&&11!==t.nodeType?t:null},parents:function(e){return eO(e,"parentNode")},parentsUntil:function(e,t,n){return eO(e,"parentNode",n)},next:function(e){return eB(e,"nextSibling")},prev:function(e){return eB(e,"previousSibling")},nextAll:function(e){return eO(e,"nextSibling")},prevAll:function(e){return eO(e,"previousSibling")},nextUntil:function(e,t,n){return eO(e,"nextSibling",n)},prevUntil:function(e,t,n){return eO(e,"previousSibling",n)},siblings:function(e){return eL((e.parentNode||{}).firstChild,e)},children:function(e){return eL(e.firstChild)},contents:function(e){return null!=e.contentDocument&&r(e.contentDocument)?e.contentDocument:(C(e,"template")&&(e=e.content||e),T.merge([],e.childNodes))}},function(e,t){T.fn[e]=function(n,r){var i=T.map(this,t,n);return"Until"!==e.slice(-5)&&(r=n),r&&"string"==typeof r&&(i=T.filter(r,i)),this.length>1&&(eF[e]||T.uniqueSort(i),eI.test(e)&&i.reverse()),this.pushStack(i)}}),T.Callbacks=function(e){e="string"==typeof e?(t=e,n={},T.each(t.match(Q)||[],function(e,t){n[t]=!0}),n):T.extend({},e);var t,n,r,i,o,a,s=[],u=[],l=-1,c=function(){for(a=a||e.once,o=r=!0;u.length;l=-1){i=u.shift();while(++l<s.length)!1===s[l].apply(i[0],i[1])&&e.stopOnFalse&&(l=s.length,i=!1)}e.memory||(i=!1),r=!1,a&&(s=i?[]:"")},f={add:function(){return s&&(i&&!r&&(l=s.length-1,u.push(i)),!function t(n){T.each(n,function(n,r){"function"==typeof r?e.unique&&f.has(r)||s.push(r):r&&r.length&&"string"!==h(r)&&t(r)})}(arguments),i&&!r&&c()),this},remove:function(){return T.each(arguments,function(e,t){var n;while((n=T.inArray(t,s,n))>-1)s.splice(n,1),n<=l&&l--}),this},has:function(e){return e?T.inArray(e,s)>-1:s.length>0},empty:function(){return s&&(s=[]),this},disable:function(){return a=u=[],s=i="",this},disabled:function(){return!s},lock:function(){return a=u=[],i||r||(s=i=""),this},locked:function(){return!!a},fireWith:function(e,t){return!a&&(t=[e,(t=t||[]).slice?t.slice():t],u.push(t),r||c()),this},fire:function(){return f.fireWith(this,arguments),this},fired:function(){return!!o}};return f},T.extend({Deferred:function(t){var n=[["notify","progress",T.Callbacks("memory"),T.Callbacks("memory"),2],["resolve","done",T.Callbacks("once memory"),T.Callbacks("once memory"),0,"resolved"],["reject","fail",T.Callbacks("once memory"),T.Callbacks("once memory"),1,"rejected"]],r="pending",i={state:function(){return r},always:function(){return o.done(arguments).fail(arguments),this},catch:function(e){return i.then(null,e)},pipe:function(){var e=arguments;return T.Deferred(function(t){T.each(n,function(n,r){var i="function"==typeof e[r[4]]&&e[r[4]];o[r[1]](function(){var e=i&&i.apply(this,arguments);e&&"function"==typeof e.promise?e.promise().progress(t.notify).done(t.resolve).fail(t.reject):t[r[0]+"With"](this,i?[e]:arguments)})}),e=null}).promise()},then:function(t,r,i){var o=0;function a(t,n,r,i){return function(){var s=this,u=arguments,l=function(){var e,l;if(!(t<o)){if((e=r.apply(s,u))===n.promise())throw TypeError("Thenable self-resolution");"function"==typeof(l=e&&("object"==typeof e||"function"==typeof e)&&e.then)?i?l.call(e,a(o,n,e_,i),a(o,n,eU,i)):(o++,l.call(e,a(o,n,e_,i),a(o,n,eU,i),a(o,n,e_,n.notifyWith))):(r!==e_&&(s=void 0,u=[e]),(i||n.resolveWith)(s,u))}},c=i?l:function(){try{l()}catch(e){T.Deferred.exceptionHook&&T.Deferred.exceptionHook(e,c.error),t+1>=o&&(r!==eU&&(s=void 0,u=[e]),n.rejectWith(s,u))}};t?c():(T.Deferred.getErrorHook&&(c.error=T.Deferred.getErrorHook()),e.setTimeout(c))}}return T.Deferred(function(e){n[0][3].add(a(0,e,"function"==typeof i?i:e_,e.notifyWith)),n[1][3].add(a(0,e,"function"==typeof t?t:e_)),n[2][3].add(a(0,e,"function"==typeof r?r:eU))}).promise()},promise:function(e){return null!=e?T.extend(e,i):i}},o={};return T.each(n,function(e,t){var a=t[2],s=t[5];i[t[1]]=a.add,s&&a.add(function(){r=s},n[3-e][2].disable,n[3-e][3].disable,n[0][2].lock,n[0][3].lock),a.add(t[3].fire),o[t[0]]=function(){return o[t[0]+"With"](this===o?void 0:this,arguments),this},o[t[0]+"With"]=a.fireWith}),i.promise(o),t&&t.call(o,o),o},when:function(e){var t=arguments.length,n=t,r=Array(n),o=i.call(arguments),a=T.Deferred(),s=function(e){return function(n){r[e]=this,o[e]=arguments.length>1?i.call(arguments):n,--t||a.resolveWith(r,o)}};if(t<=1&&(eX(e,a.done(s(n)).resolve,a.reject,!t),"pending"===a.state()||"function"==typeof(o[n]&&o[n].then)))return a.then();while(n--)eX(o[n],s(n),a.reject);return a.promise()}});var ez=/^(Eval|Internal|Range|Reference|Syntax|Type|URI)Error$/;T.Deferred.exceptionHook=function(t,n){t&&ez.test(t.name)&&e.console.warn("jQuery.Deferred exception",t,n)},T.readyException=function(t){e.setTimeout(function(){throw t})};var eY=T.Deferred();function eG(){y.removeEventListener("DOMContentLoaded",eG),e.removeEventListener("load",eG),T.ready()}T.fn.ready=function(e){return eY.then(e).catch(function(e){T.readyException(e)}),this},T.extend({isReady:!1,readyWait:1,ready:function(e){(!0===e?--T.readyWait:T.isReady)||(T.isReady=!0,!0!==e&&--T.readyWait>0||eY.resolveWith(y,[T]))}}),T.ready.then=eY.then,"loading"!==y.readyState?e.setTimeout(T.ready):(y.addEventListener("DOMContentLoaded",eG),e.addEventListener("load",eG));var eV=/-([a-z])/g;function eQ(e,t){return t.toUpperCase()}function eJ(e){return e.replace(eV,eQ)}function eK(e){return 1===e.nodeType||9===e.nodeType||!+e.nodeType}function eZ(){this.expando=T.expando+eZ.uid++}eZ.uid=1,eZ.prototype={cache:function(e){var t=e[this.expando];return!t&&(t=Object.create(null),eK(e)&&(e.nodeType?e[this.expando]=t:Object.defineProperty(e,this.expando,{value:t,configurable:!0}))),t},set:function(e,t,n){var r,i=this.cache(e);if("string"==typeof t)i[eJ(t)]=n;else for(r in t)i[eJ(r)]=t[r];return n},get:function(e,t){return void 0===t?this.cache(e):e[this.expando]&&e[this.expando][eJ(t)]},access:function(e,t,n){return void 0===t||t&&"string"==typeof t&&void 0===n?this.get(e,t):(this.set(e,t,n),void 0!==n?n:t)},remove:function(e,t){var n,r=e[this.expando];if(void 0!==r){if(void 0!==t){n=(t=Array.isArray(t)?t.map(eJ):(t=eJ(t))in r?[t]:t.match(Q)||[]).length;while(n--)delete r[t[n]]}(void 0===t||T.isEmptyObject(r))&&(e.nodeType?e[this.expando]=void 0:delete e[this.expando])}},hasData:function(e){var t=e[this.expando];return void 0!==t&&!T.isEmptyObject(t)}};var e0=new eZ,e1=new eZ,e2=/^(?:\{[\w\W]*\}|\[[\w\W]*\])$/,e3=/[A-Z]/g;function e4(e,t,n){var r,i;if(void 0===n&&1===e.nodeType)if(r="data-"+t.replace(e3,"-$&").toLowerCase(),"string"==typeof(n=e.getAttribute(r))){try{i=n,n="true"===i||"false"!==i&&("null"===i?null:i===+i+""?+i:e2.test(i)?JSON.parse(i):i)}catch(e){}e1.set(e,t,n)}else n=void 0;return n}T.extend({hasData:function(e){return e1.hasData(e)||e0.hasData(e)},data:function(e,t,n){return e1.access(e,t,n)},removeData:function(e,t){e1.remove(e,t)},_data:function(e,t,n){return e0.access(e,t,n)},_removeData:function(e,t){e0.remove(e,t)}}),T.fn.extend({data:function(e,t){var n,r,i,o=this[0],a=o&&o.attributes;if(void 0===e){if(this.length&&(i=e1.get(o),1===o.nodeType&&!e0.get(o,"hasDataAttrs"))){n=a.length;while(n--)a[n]&&0===(r=a[n].name).indexOf("data-")&&e4(o,r=eJ(r.slice(5)),i[r]);e0.set(o,"hasDataAttrs",!0)}return i}return"object"==typeof e?this.each(function(){e1.set(this,e)}):V(this,function(t){var n;if(o&&void 0===t)return void 0!==(n=e1.get(o,e))||void 0!==(n=e4(o,e))?n:void 0;this.each(function(){e1.set(this,e,t)})},null,t,arguments.length>1,null,!0)},removeData:function(e){return this.each(function(){e1.remove(this,e)})}}),T.extend({queue:function(e,t,n){var r;if(e)return t=(t||"fx")+"queue",r=e0.get(e,t),n&&(!r||Array.isArray(n)?r=e0.set(e,t,T.makeArray(n)):r.push(n)),r||[]},dequeue:function(e,t){t=t||"fx";var n=T.queue(e,t),r=n.length,i=n.shift(),o=T._queueHooks(e,t);"inprogress"===i&&(i=n.shift(),r--),i&&("fx"===t&&n.unshift("inprogress"),delete o.stop,i.call(e,function(){T.dequeue(e,t)},o)),!r&&o&&o.empty.fire()},_queueHooks:function(e,t){var n=t+"queueHooks";return e0.get(e,n)||e0.set(e,n,{empty:T.Callbacks("once memory").add(function(){e0.remove(e,[t+"queue",n])})})}}),T.fn.extend({queue:function(e,t){var n=2;return("string"!=typeof e&&(t=e,e="fx",n--),arguments.length<n)?T.queue(this[0],e):void 0===t?this:this.each(function(){var n=T.queue(this,e,t);T._queueHooks(this,e),"fx"===e&&"inprogress"!==n[0]&&T.dequeue(this,e)})},dequeue:function(e){return this.each(function(){T.dequeue(this,e)})},clearQueue:function(e){return this.queue(e||"fx",[])},promise:function(e,t){var n,r=1,i=T.Deferred(),o=this,a=this.length,s=function(){--r||i.resolveWith(o,[o])};"string"!=typeof e&&(t=e,e=void 0),e=e||"fx";while(a--)(n=e0.get(o[a],e+"queueHooks"))&&n.empty&&(r++,n.empty.add(s));return s(),i.promise(t)}});var e5=/[+-]?(?:\d*\.|)\d+(?:[eE][+-]?\d+|)/.source,e9=RegExp("^(?:([+-])=|)("+e5+")([a-z%]*)$","i"),e6=["Top","Right","Bottom","Left"];function e8(e,t){return"none"===(e=t||e).style.display||""===e.style.display&&"none"===T.css(e,"display")}var e7=/^[a-z]/,te=/^(?:Border(?:Top|Right|Bottom|Left)?(?:Width|)|(?:Margin|Padding)?(?:Top|Right|Bottom|Left)?|(?:Min|Max)?(?:Width|Height))$/;function tt(e){return e7.test(e)&&te.test(e[0].toUpperCase()+e.slice(1))}function tn(e,t,n,r){var i,o,a=20,s=r?function(){return r.cur()}:function(){return T.css(e,t,"")},u=s(),l=n&&n[3]||(tt(t)?"px":""),c=e.nodeType&&(!tt(t)||"px"!==l&&+u)&&e9.exec(T.css(e,t));if(c&&c[3]!==l){u/=2,l=l||c[3],c=+u||1;while(a--)T.style(e,t,c+l),(1-o)*(1-(o=s()/u||.5))<=0&&(a=0),c/=o;c*=2,T.style(e,t,c+l),n=n||[]}return n&&(c=+c||+u||0,i=n[1]?c+(n[1]+1)*n[2]:+n[2],r&&(r.unit=l,r.start=c,r.end=i)),i}var tr=/^-ms-/;function ti(e){return eJ(e.replace(tr,"ms-"))}var to={};function ta(e,t){for(var n,r,i=[],o=0,a=e.length;o<a;o++)(r=e[o]).style&&(n=r.style.display,t?("none"===n&&(i[o]=e0.get(r,"display")||null,i[o]||(r.style.display="")),""===r.style.display&&e8(r)&&(i[o]=function(e){var t,n=e.ownerDocument,r=e.nodeName,i=to[r];return i||(t=n.body.appendChild(n.createElement(r)),i=T.css(t,"display"),t.parentNode.removeChild(t),"none"===i&&(i="block"),to[r]=i),i}(r))):"none"!==n&&(i[o]="none",e0.set(r,"display",n)));for(o=0;o<a;o++)null!=i[o]&&(e[o].style.display=i[o]);return e}T.fn.extend({show:function(){return ta(this,!0)},hide:function(){return ta(this)},toggle:function(e){return"boolean"==typeof e?e?this.show():this.hide():this.each(function(){e8(this)?T(this).show():T(this).hide()})}});var ts=function(e){return T.contains(e.ownerDocument,e)||e.getRootNode(tu)===e.ownerDocument},tu={composed:!0};L.getRootNode||(ts=function(e){return T.contains(e.ownerDocument,e)});var tl=/<([a-z][^\/\0>\x20\t\r\n\f]*)/i,tc={thead:["table"],col:["colgroup","table"],tr:["tbody","table"],td:["tr","tbody","table"]};function tf(e,t){var r;return(r=void 0!==e.getElementsByTagName?n.slice.call(e.getElementsByTagName(t||"*")):void 0!==e.querySelectorAll?e.querySelectorAll(t||"*"):[],void 0===t||t&&C(e,t))?T.merge([e],r):r}tc.tbody=tc.tfoot=tc.colgroup=tc.caption=tc.thead,tc.th=tc.td;var tp=/^$|^module$|\/(?:java|ecma)script/i;function td(e,t){for(var n=0,r=e.length;n<r;n++)e0.set(e[n],"globalEval",!t||e0.get(t[n],"globalEval"))}var th=/<|&#?\w+;/;function tg(e,t,r,i,o){for(var a,s,u,l,c,f=t.createDocumentFragment(),p=[],d=0,g=e.length;d<g;d++)if((a=e[d])||0===a)if("object"===h(a)&&(a.nodeType||v(a)))T.merge(p,a.nodeType?[a]:a);else if(th.test(a)){s=s||f.appendChild(t.createElement("div")),c=(u=tc[(tl.exec(a)||["",""])[1].toLowerCase()]||n).length;while(--c>-1)s=s.appendChild(t.createElement(u[c]));s.innerHTML=T.htmlPrefilter(a),T.merge(p,s.childNodes),(s=f.firstChild).textContent=""}else p.push(t.createTextNode(a));f.textContent="",d=0;while(a=p[d++]){if(i&&T.inArray(a,i)>-1){o&&o.push(a);continue}if(l=ts(a),s=tf(f.appendChild(a),"script"),l&&td(s),r){c=0;while(a=s[c++])tp.test(a.type||"")&&r.push(a)}}return f}function tv(e){return e.type=(null!==e.getAttribute("type"))+"/"+e.type,e}function ty(e){return"true/"===(e.type||"").slice(0,5)?e.type=e.type.slice(5):e.removeAttribute("type"),e}function tm(e,t,n,r){t=o(t);var i,a,s,u,l,c,f=0,p=e.length,d=p-1,h=t[0];if("function"==typeof h)return e.each(function(i){var o=e.eq(i);t[0]=h.call(this,i,o.html()),tm(o,t,n,r)});if(p&&(a=(i=tg(t,e[0].ownerDocument,!1,e,r)).firstChild,1===i.childNodes.length&&(i=a),a||r)){for(u=(s=T.map(tf(i,"script"),tv)).length;f<p;f++)l=i,f!==d&&(l=T.clone(l,!0,!0),u&&T.merge(s,tf(l,"script"))),n.call(e[f],l,f);if(u)for(c=s[s.length-1].ownerDocument,T.map(s,ty),f=0;f<u;f++)l=s[f],tp.test(l.type||"")&&!e0.get(l,"globalEval")&&T.contains(c,l)&&(l.src&&"module"!==(l.type||"").toLowerCase()?T._evalUrl&&!l.noModule&&T._evalUrl(l.src,{nonce:l.nonce,crossOrigin:l.crossOrigin},c):x(l.textContent,l,c))}return e}var tx=/^(?:checkbox|radio)$/i,tb=/^([^.]*)(?:\.(.+)|)/;function tw(){return!0}function tT(){return!1}function tC(e,t,n,r,i,o){var a,s;if("object"==typeof t){for(s in"string"!=typeof n&&(r=r||n,n=void 0),t)tC(e,s,n,r,t[s],o);return e}if(null==r&&null==i?(i=n,r=n=void 0):null==i&&("string"==typeof n?(i=r,r=void 0):(i=r,r=n,n=void 0)),!1===i)i=tT;else if(!i)return e;return 1===o&&(a=i,(i=function(e){return T().off(e),a.apply(this,arguments)}).guid=a.guid||(a.guid=T.guid++)),e.each(function(){T.event.add(this,t,i,r,n)})}function tj(e,t,n){if(!n){void 0===e0.get(e,t)&&T.event.add(e,t,tw);return}e0.set(e,t,!1),T.event.add(e,t,{namespace:!1,handler:function(e){var n,r=e0.get(this,t);if(1&e.isTrigger&&this[t]){if(r.length)(T.event.special[t]||{}).delegateType&&e.stopPropagation();else if(r=i.call(arguments),e0.set(this,t,r),this[t](),n=e0.get(this,t),e0.set(this,t,!1),r!==n)return e.stopImmediatePropagation(),e.preventDefault(),n&&n.value}else r.length&&(e0.set(this,t,{value:T.event.trigger(r[0],r.slice(1),this)}),e.stopPropagation(),e.isImmediatePropagationStopped=tw)}})}T.event={add:function(e,t,n,r,i){var o,a,s,u,l,c,f,p,d,h,g,v=e0.get(e);if(eK(e)){n.handler&&(n=(o=n).handler,i=o.selector),i&&T.find.matchesSelector(L,i),n.guid||(n.guid=T.guid++),(u=v.events)||(u=v.events=Object.create(null)),(a=v.handle)||(a=v.handle=function(t){return T.event.triggered!==t.type?T.event.dispatch.apply(e,arguments):void 0}),l=(t=(t||"").match(Q)||[""]).length;while(l--){if(d=g=(s=tb.exec(t[l])||[])[1],h=(s[2]||"").split(".").sort(),!d)continue;f=T.event.special[d]||{},d=(i?f.delegateType:f.bindType)||d,f=T.event.special[d]||{},c=T.extend({type:d,origType:g,data:r,handler:n,guid:n.guid,selector:i,needsContext:i&&T.expr.match.needsContext.test(i),namespace:h.join(".")},o),(p=u[d])||((p=u[d]=[]).delegateCount=0,(!f.setup||!1===f.setup.call(e,r,h,a))&&e.addEventListener&&e.addEventListener(d,a)),f.add&&(f.add.call(e,c),c.handler.guid||(c.handler.guid=n.guid)),i?p.splice(p.delegateCount++,0,c):p.push(c)}}},remove:function(e,t,n,r,i){var o,a,s,u,l,c,f,p,d,h,g,v=e0.hasData(e)&&e0.get(e);if(v&&(u=v.events)){l=(t=(t||"").match(Q)||[""]).length;while(l--){if(d=g=(s=tb.exec(t[l])||[])[1],h=(s[2]||"").split(".").sort(),!d){for(d in u)T.event.remove(e,d+t[l],n,r,!0);continue}f=T.event.special[d]||{},p=u[d=(r?f.delegateType:f.bindType)||d]||[],s=s[2]&&RegExp("(^|\\.)"+h.join("\\.(?:.*\\.|)")+"(\\.|$)"),a=o=p.length;while(o--)c=p[o],(i||g===c.origType)&&(!n||n.guid===c.guid)&&(!s||s.test(c.namespace))&&(!r||r===c.selector||"**"===r&&c.selector)&&(p.splice(o,1),c.selector&&p.delegateCount--,f.remove&&f.remove.call(e,c));a&&!p.length&&(f.teardown&&!1!==f.teardown.call(e,h,v.handle)||T.removeEvent(e,d,v.handle),delete u[d])}T.isEmptyObject(u)&&e0.remove(e,"handle events")}},dispatch:function(e){var t,n,r,i,o,a,s=Array(arguments.length),u=T.event.fix(e),l=(e0.get(this,"events")||Object.create(null))[u.type]||[],c=T.event.special[u.type]||{};for(t=1,s[0]=u;t<arguments.length;t++)s[t]=arguments[t];if(u.delegateTarget=this,!c.preDispatch||!1!==c.preDispatch.call(this,u)){a=T.event.handlers.call(this,u,l),t=0;while((i=a[t++])&&!u.isPropagationStopped()){u.currentTarget=i.elem,n=0;while((o=i.handlers[n++])&&!u.isImmediatePropagationStopped())(!u.rnamespace||!1===o.namespace||u.rnamespace.test(o.namespace))&&(u.handleObj=o,u.data=o.data,void 0!==(r=((T.event.special[o.origType]||{}).handle||o.handler).apply(i.elem,s))&&!1===(u.result=r)&&(u.preventDefault(),u.stopPropagation()))}return c.postDispatch&&c.postDispatch.call(this,u),u.result}},handlers:function(e,t){var n,r,i,o,a,s=[],u=t.delegateCount,l=e.target;if(u&&!("click"===e.type&&e.button>=1)){for(;l!==this;l=l.parentNode||this)if(1===l.nodeType&&("click"!==e.type||!0!==l.disabled)){for(n=0,o=[],a={};n<u;n++)void 0===a[i=(r=t[n]).selector+" "]&&(a[i]=r.needsContext?T(i,this).index(l)>-1:T.find(i,this,null,[l]).length),a[i]&&o.push(r);o.length&&s.push({elem:l,handlers:o})}}return l=this,u<t.length&&s.push({elem:l,handlers:t.slice(u)}),s},addProp:function(e,t){Object.defineProperty(T.Event.prototype,e,{enumerable:!0,configurable:!0,get:"function"==typeof t?function(){if(this.originalEvent)return t(this.originalEvent)}:function(){if(this.originalEvent)return this.originalEvent[e]},set:function(t){Object.defineProperty(this,e,{enumerable:!0,configurable:!0,writable:!0,value:t})}})},fix:function(e){return e[T.expando]?e:new T.Event(e)},special:T.extend(Object.create(null),{load:{noBubble:!0},click:{setup:function(e){var t=this||e;return tx.test(t.type)&&t.click&&C(t,"input")&&tj(t,"click",!0),!1},trigger:function(e){var t=this||e;return tx.test(t.type)&&t.click&&C(t,"input")&&tj(t,"click"),!0},_default:function(e){var t=e.target;return tx.test(t.type)&&t.click&&C(t,"input")&&e0.get(t,"click")||C(t,"a")}},beforeunload:{postDispatch:function(e){void 0!==e.result&&e.preventDefault()}}})},T.removeEvent=function(e,t,n){e.removeEventListener&&e.removeEventListener(t,n)},T.Event=function(e,t){if(!(this instanceof T.Event))return new T.Event(e,t);e&&e.type?(this.originalEvent=e,this.type=e.type,this.isDefaultPrevented=e.defaultPrevented?tw:tT,this.target=e.target,this.currentTarget=e.currentTarget,this.relatedTarget=e.relatedTarget):this.type=e,t&&T.extend(this,t),this.timeStamp=e&&e.timeStamp||Date.now(),this[T.expando]=!0},T.Event.prototype={constructor:T.Event,isDefaultPrevented:tT,isPropagationStopped:tT,isImmediatePropagationStopped:tT,isSimulated:!1,preventDefault:function(){var e=this.originalEvent;this.isDefaultPrevented=tw,e&&!this.isSimulated&&e.preventDefault()},stopPropagation:function(){var e=this.originalEvent;this.isPropagationStopped=tw,e&&!this.isSimulated&&e.stopPropagation()},stopImmediatePropagation:function(){var e=this.originalEvent;this.isImmediatePropagationStopped=tw,e&&!this.isSimulated&&e.stopImmediatePropagation(),this.stopPropagation()}},T.each({altKey:!0,bubbles:!0,cancelable:!0,changedTouches:!0,ctrlKey:!0,detail:!0,eventPhase:!0,metaKey:!0,pageX:!0,pageY:!0,shiftKey:!0,view:!0,char:!0,code:!0,charCode:!0,key:!0,keyCode:!0,button:!0,buttons:!0,clientX:!0,clientY:!0,offsetX:!0,offsetY:!0,pointerId:!0,pointerType:!0,screenX:!0,screenY:!0,targetTouches:!0,toElement:!0,touches:!0,which:!0},T.event.addProp),T.each({focus:"focusin",blur:"focusout"},function(e,t){function n(e){var t=T.event.fix(e);t.type="focusin"===e.type?"focus":"blur",t.isSimulated=!0,t.target===t.currentTarget&&e0.get(this,"handle")(t)}T.event.special[e]={setup:function(){if(tj(this,e,!0),!k)return!1;this.addEventListener(t,n)},trigger:function(){return tj(this,e),!0},teardown:function(){if(!k)return!1;this.removeEventListener(t,n)},_default:function(t){return e0.get(t.target,e)},delegateType:t}}),T.each({mouseenter:"mouseover",mouseleave:"mouseout",pointerenter:"pointerover",pointerleave:"pointerout"},function(e,t){T.event.special[e]={delegateType:t,bindType:t,handle:function(e){var n,r=e.relatedTarget,i=e.handleObj;return r&&(r===this||T.contains(this,r))||(e.type=i.origType,n=i.handler.apply(this,arguments),e.type=t),n}}}),T.fn.extend({on:function(e,t,n,r){return tC(this,e,t,n,r)},one:function(e,t,n,r){return tC(this,e,t,n,r,1)},off:function(e,t,n){var r,i;if(e&&e.preventDefault&&e.handleObj)return r=e.handleObj,T(e.delegateTarget).off(r.namespace?r.origType+"."+r.namespace:r.origType,r.selector,r.handler),this;if("object"==typeof e){for(i in e)this.off(i,t,e[i]);return this}return(!1===t||"function"==typeof t)&&(n=t,t=void 0),!1===n&&(n=tT),this.each(function(){T.event.remove(this,e,n,t)})}});var tE=/<script|<style|<link/i;function tk(e,t){return C(e,"table")&&C(11!==t.nodeType?t:t.firstChild,"tr")&&T(e).children("tbody")[0]||e}function tS(e,t){var n,r,i,o=e0.get(e,"events");if(1===t.nodeType){if(o)for(n in e0.remove(t,"handle events"),o)for(r=0,i=o[n].length;r<i;r++)T.event.add(t,n,o[n][r]);e1.hasData(e)&&e1.set(t,T.extend({},e1.get(e)))}}function tD(e,t,n){for(var r,i=t?T.filter(t,e):e,o=0;null!=(r=i[o]);o++)n||1!==r.nodeType||T.cleanData(tf(r)),r.parentNode&&(n&&ts(r)&&td(tf(r,"script")),r.parentNode.removeChild(r));return e}T.extend({htmlPrefilter:function(e){return e},clone:function(e,t,n){var r,i,o,a,s=e.cloneNode(!0),u=ts(e);if(k&&(1===e.nodeType||11===e.nodeType)&&!T.isXMLDoc(e))for(r=0,a=tf(s),i=(o=tf(e)).length;r<i;r++)C(a[r],"textarea")&&(a[r].defaultValue=o[r].defaultValue);if(t)if(n)for(r=0,o=o||tf(e),a=a||tf(s),i=o.length;r<i;r++)tS(o[r],a[r]);else tS(e,s);return(a=tf(s,"script")).length>0&&td(a,!u&&tf(e,"script")),s},cleanData:function(e){for(var t,n,r,i=T.event.special,o=0;void 0!==(n=e[o]);o++)if(eK(n)){if(t=n[e0.expando]){if(t.events)for(r in t.events)i[r]?T.event.remove(n,r):T.removeEvent(n,r,t.handle);n[e0.expando]=void 0}n[e1.expando]&&(n[e1.expando]=void 0)}}}),T.fn.extend({detach:function(e){return tD(this,e,!0)},remove:function(e){return tD(this,e)},text:function(e){return V(this,function(e){return void 0===e?T.text(this):this.empty().each(function(){(1===this.nodeType||11===this.nodeType||9===this.nodeType)&&(this.textContent=e)})},null,e,arguments.length)},append:function(){return tm(this,arguments,function(e){(1===this.nodeType||11===this.nodeType||9===this.nodeType)&&tk(this,e).appendChild(e)})},prepend:function(){return tm(this,arguments,function(e){if(1===this.nodeType||11===this.nodeType||9===this.nodeType){var t=tk(this,e);t.insertBefore(e,t.firstChild)}})},before:function(){return tm(this,arguments,function(e){this.parentNode&&this.parentNode.insertBefore(e,this)})},after:function(){return tm(this,arguments,function(e){this.parentNode&&this.parentNode.insertBefore(e,this.nextSibling)})},empty:function(){for(var e,t=0;null!=(e=this[t]);t++)1===e.nodeType&&(T.cleanData(tf(e,!1)),e.textContent="");return this},clone:function(e,t){return e=null!=e&&e,t=null==t?e:t,this.map(function(){return T.clone(this,e,t)})},html:function(e){return V(this,function(e){var t=this[0]||{},n=0,r=this.length;if(void 0===e&&1===t.nodeType)return t.innerHTML;if("string"==typeof e&&!tE.test(e)&&!tc[(tl.exec(e)||["",""])[1].toLowerCase()]){e=T.htmlPrefilter(e);try{for(;n<r;n++)t=this[n]||{},1===t.nodeType&&(T.cleanData(tf(t,!1)),t.innerHTML=e);t=0}catch(e){}}t&&this.empty().append(e)},null,e,arguments.length)},replaceWith:function(){var e=[];return tm(this,arguments,function(t){var n=this.parentNode;0>T.inArray(this,e)&&(T.cleanData(tf(this)),n&&n.replaceChild(t,this))},e)}}),T.each({appendTo:"append",prependTo:"prepend",insertBefore:"before",insertAfter:"after",replaceAll:"replaceWith"},function(e,t){T.fn[e]=function(e){for(var n,r=[],i=T(e),o=i.length-1,s=0;s<=o;s++)n=s===o?this:this.clone(!0),T(i[s])[t](n),a.apply(r,n);return this.pushStack(r)}});var tA=RegExp("^("+e5+")(?!px)[a-z%]+$","i"),tN=/^--/;function tq(t){var n=t.ownerDocument.defaultView;return n||(n=e),n.getComputedStyle(t)}function tO(e,t,n){var r,i=tN.test(t);return(n=n||tq(e))&&(r=n.getPropertyValue(t)||n[t],i&&r&&(r=r.replace(D,"$1")||void 0),""!==r||ts(e)||(r=T.style(e,t))),void 0!==r?r+"":r}var tL=["Webkit","Moz","ms"],tH=y.createElement("div").style;function tP(e){return e in tH?e:function(e){var t=e[0].toUpperCase()+e.slice(1),n=tL.length;while(n--)if((e=tL[n]+t)in tH)return e}(e)||e}var tR,tM,tW=y.createElement("table");function t$(){if(tW&&tW.style){var t,n=y.createElement("col"),r=y.createElement("tr"),i=y.createElement("td");if(tW.style.cssText="position:absolute;left:-11111px;border-collapse:separate;border-spacing:0",r.style.cssText="box-sizing:content-box;border:1px solid;height:1px",i.style.cssText="height:9px;width:9px;padding:0",n.span=2,L.appendChild(tW).appendChild(n).parentNode.appendChild(r).appendChild(i).parentNode.appendChild(i.cloneNode(!0)),0===tW.offsetWidth)return void L.removeChild(tW);t=e.getComputedStyle(r),tM=k||18===Math.round(parseFloat(e.getComputedStyle(n).width)),tR=Math.round(parseFloat(t.height)+parseFloat(t.borderTopWidth)+parseFloat(t.borderBottomWidth))===r.offsetHeight,L.removeChild(tW),tW=null}}T.extend(d,{reliableTrDimensions:function(){return t$(),tR},reliableColDimensions:function(){return t$(),tM}});var tI={position:"absolute",visibility:"hidden",display:"block"},tF={letterSpacing:"0",fontWeight:"400"};function tB(e,t,n){var r=e9.exec(t);return r?Math.max(0,r[2]-(n||0))+(r[3]||"px"):t}function t_(e,t,n,r,i,o){var a=+("width"===t),s=0,u=0,l=0;if(n===(r?"border":"content"))return 0;for(;a<4;a+=2)"margin"===n&&(l+=T.css(e,n+e6[a],!0,i)),r?("content"===n&&(u-=T.css(e,"padding"+e6[a],!0,i)),"margin"!==n&&(u-=T.css(e,"border"+e6[a]+"Width",!0,i))):(u+=T.css(e,"padding"+e6[a],!0,i),"padding"!==n?u+=T.css(e,"border"+e6[a]+"Width",!0,i):s+=T.css(e,"border"+e6[a]+"Width",!0,i));return!r&&o>=0&&(u+=Math.max(0,Math.ceil(e["offset"+t[0].toUpperCase()+t.slice(1)]-o-u-s-.5))||0),u+l}function tU(e,t,n){var r=tq(e),i=(k||n)&&"border-box"===T.css(e,"boxSizing",!1,r),o=i,a=tO(e,t,r),s="offset"+t[0].toUpperCase()+t.slice(1);if(tA.test(a)){if(!n)return a;a="auto"}return("auto"===a||k&&i||!d.reliableColDimensions()&&C(e,"col")||!d.reliableTrDimensions()&&C(e,"tr"))&&e.getClientRects().length&&(i="border-box"===T.css(e,"boxSizing",!1,r),(o=s in e)&&(a=e[s])),(a=parseFloat(a)||0)+t_(e,t,n||(i?"border":"content"),o,r,a)+"px"}function tX(e,t,n,r,i){return new tX.prototype.init(e,t,n,r,i)}T.extend({cssHooks:{},style:function(e,t,n,r){if(e&&3!==e.nodeType&&8!==e.nodeType&&e.style){var i,o,a,s=ti(t),u=tN.test(t),l=e.style;if(u||(t=tP(s)),a=T.cssHooks[t]||T.cssHooks[s],void 0===n)return a&&"get"in a&&void 0!==(i=a.get(e,!1,r))?i:l[t];if("string"==(o=typeof n)&&(i=e9.exec(n))&&i[1]&&(n=tn(e,t,i),o="number"),null!=n&&n==n)"number"===o&&(n+=i&&i[3]||(tt(s)?"px":"")),k&&""===n&&0===t.indexOf("background")&&(l[t]="inherit"),a&&"set"in a&&void 0===(n=a.set(e,n,r))||(u?l.setProperty(t,n):l[t]=n)}},css:function(e,t,n,r){var i,o,a,s=ti(t);return(tN.test(t)||(t=tP(s)),(a=T.cssHooks[t]||T.cssHooks[s])&&"get"in a&&(i=a.get(e,!0,n)),void 0===i&&(i=tO(e,t,r)),"normal"===i&&t in tF&&(i=tF[t]),""===n||n)?(o=parseFloat(i),!0===n||isFinite(o)?o||0:i):i}}),T.each(["height","width"],function(e,t){T.cssHooks[t]={get:function(e,n,r){if(n)return"none"===T.css(e,"display")?function(e,t,n){var r,i,o={};for(i in t)o[i]=e.style[i],e.style[i]=t[i];for(i in r=n.call(e),t)e.style[i]=o[i];return r}(e,tI,function(){return tU(e,t,r)}):tU(e,t,r)},set:function(e,n,r){var i,o=tq(e),a=r&&"border-box"===T.css(e,"boxSizing",!1,o),s=r?t_(e,t,r,a,o):0;return s&&(i=e9.exec(n))&&"px"!==(i[3]||"px")&&(e.style[t]=n,n=T.css(e,t)),tB(e,n,s)}}}),T.each({margin:"",padding:"",border:"Width"},function(e,t){T.cssHooks[e+t]={expand:function(n){for(var r=0,i={},o="string"==typeof n?n.split(" "):[n];r<4;r++)i[e+e6[r]+t]=o[r]||o[r-2]||o[0];return i}},"margin"!==e&&(T.cssHooks[e+t].set=tB)}),T.fn.extend({css:function(e,t){return V(this,function(e,t,n){var r,i,o={},a=0;if(Array.isArray(t)){for(r=tq(e),i=t.length;a<i;a++)o[t[a]]=T.css(e,t[a],!1,r);return o}return void 0!==n?T.style(e,t,n):T.css(e,t)},e,t,arguments.length>1)}}),T.Tween=tX,tX.prototype={constructor:tX,init:function(e,t,n,r,i,o){this.elem=e,this.prop=n,this.easing=i||T.easing._default,this.options=t,this.start=this.now=this.cur(),this.end=r,this.unit=o||(tt(n)?"px":"")},cur:function(){var e=tX.propHooks[this.prop];return e&&e.get?e.get(this):tX.propHooks._default.get(this)},run:function(e){var t,n=tX.propHooks[this.prop];return this.options.duration?this.pos=t=T.easing[this.easing](e,this.options.duration*e,0,1,this.options.duration):this.pos=t=e,this.now=(this.end-this.start)*t+this.start,this.options.step&&this.options.step.call(this.elem,this.now,this),n&&n.set?n.set(this):tX.propHooks._default.set(this),this}},tX.prototype.init.prototype=tX.prototype,tX.propHooks={_default:{get:function(e){var t;return 1!==e.elem.nodeType||null!=e.elem[e.prop]&&null==e.elem.style[e.prop]?e.elem[e.prop]:(t=T.css(e.elem,e.prop,""))&&"auto"!==t?t:0},set:function(e){T.fx.step[e.prop]?T.fx.step[e.prop](e):1===e.elem.nodeType&&(T.cssHooks[e.prop]||null!=e.elem.style[tP(e.prop)])?T.style(e.elem,e.prop,e.now+e.unit):e.elem[e.prop]=e.now}}},T.easing={linear:function(e){return e},swing:function(e){return .5-Math.cos(e*Math.PI)/2},_default:"swing"},T.fx=tX.prototype.init,T.fx.step={};var tz,tY,tG=/^(?:toggle|show|hide)$/,tV=/queueHooks$/;function tQ(){return e.setTimeout(function(){tz=void 0}),tz=Date.now()}function tJ(e,t){var n,r=0,i={height:e};for(t=+!!t;r<4;r+=2-t)i["margin"+(n=e6[r])]=i["padding"+n]=e;return t&&(i.opacity=i.width=e),i}function tK(e,t,n){for(var r,i=(tZ.tweeners[t]||[]).concat(tZ.tweeners["*"]),o=0,a=i.length;o<a;o++)if(r=i[o].call(n,t,e))return r}function tZ(e,t,n){var r,i,o=0,a=tZ.prefilters.length,s=T.Deferred().always(function(){delete u.elem}),u=function(){if(i)return!1;for(var t=tz||tQ(),n=Math.max(0,l.startTime+l.duration-t),r=1-(n/l.duration||0),o=0,a=l.tweens.length;o<a;o++)l.tweens[o].run(r);return(s.notifyWith(e,[l,r,n]),r<1&&a)?n:(a||s.notifyWith(e,[l,1,0]),s.resolveWith(e,[l]),!1)},l=s.promise({elem:e,props:T.extend({},t),opts:T.extend(!0,{specialEasing:{},easing:T.easing._default},n),originalProperties:t,originalOptions:n,startTime:tz||tQ(),duration:n.duration,tweens:[],createTween:function(t,n){var r=T.Tween(e,l.opts,t,n,l.opts.specialEasing[t]||l.opts.easing);return l.tweens.push(r),r},stop:function(t){var n=0,r=t?l.tweens.length:0;if(i)return this;for(i=!0;n<r;n++)l.tweens[n].run(1);return t?(s.notifyWith(e,[l,1,0]),s.resolveWith(e,[l,t])):s.rejectWith(e,[l,t]),this}}),c=l.props;for(!function(e,t){var n,r,i,o,a;for(n in e)if(i=t[r=ti(n)],Array.isArray(o=e[n])&&(i=o[1],o=e[n]=o[0]),n!==r&&(e[r]=o,delete e[n]),(a=T.cssHooks[r])&&"expand"in a)for(n in o=a.expand(o),delete e[r],o)n in e||(e[n]=o[n],t[n]=i);else t[r]=i}(c,l.opts.specialEasing);o<a;o++)if(r=tZ.prefilters[o].call(l,e,c,l.opts))return"function"==typeof r.stop&&(T._queueHooks(l.elem,l.opts.queue).stop=r.stop.bind(r)),r;return T.map(c,tK,l),"function"==typeof l.opts.start&&l.opts.start.call(e,l),l.progress(l.opts.progress).done(l.opts.done,l.opts.complete).fail(l.opts.fail).always(l.opts.always),T.fx.timer(T.extend(u,{elem:e,anim:l,queue:l.opts.queue})),l}T.Animation=T.extend(tZ,{tweeners:{"*":[function(e,t){var n=this.createTween(e,t);return tn(n.elem,e,e9.exec(t),n),n}]},tweener:function(e,t){"function"==typeof e?(t=e,e=["*"]):e=e.match(Q);for(var n,r=0,i=e.length;r<i;r++)n=e[r],tZ.tweeners[n]=tZ.tweeners[n]||[],tZ.tweeners[n].unshift(t)},prefilters:[function(e,t,n){var r,i,o,a,s,u,l,c,f="width"in t||"height"in t,p=this,d={},h=e.style,g=e.nodeType&&e8(e),v=e0.get(e,"fxshow");for(r in n.queue||(null==(a=T._queueHooks(e,"fx")).unqueued&&(a.unqueued=0,s=a.empty.fire,a.empty.fire=function(){a.unqueued||s()}),a.unqueued++,p.always(function(){p.always(function(){a.unqueued--,T.queue(e,"fx").length||a.empty.fire()})})),t)if(i=t[r],tG.test(i)){if(delete t[r],o=o||"toggle"===i,i===(g?"hide":"show"))if("show"!==i||!v||void 0===v[r])continue;else g=!0;d[r]=v&&v[r]||T.style(e,r)}if(!(!(u=!T.isEmptyObject(t))&&T.isEmptyObject(d)))for(r in f&&1===e.nodeType&&(n.overflow=[h.overflow,h.overflowX,h.overflowY],null==(l=v&&v.display)&&(l=e0.get(e,"display")),"none"===(c=T.css(e,"display"))&&(l?c=l:(ta([e],!0),l=e.style.display||l,c=T.css(e,"display"),ta([e]))),("inline"===c||"inline-block"===c&&null!=l)&&"none"===T.css(e,"float")&&(u||(p.done(function(){h.display=l}),null==l&&(l="none"===(c=h.display)?"":c)),h.display="inline-block")),n.overflow&&(h.overflow="hidden",p.always(function(){h.overflow=n.overflow[0],h.overflowX=n.overflow[1],h.overflowY=n.overflow[2]})),u=!1,d)u||(v?"hidden"in v&&(g=v.hidden):v=e0.set(e,"fxshow",{display:l}),o&&(v.hidden=!g),g&&ta([e],!0),p.done(function(){for(r in g||ta([e]),e0.remove(e,"fxshow"),d)T.style(e,r,d[r])})),u=tK(g?v[r]:0,r,p),r in v||(v[r]=u.start,g&&(u.end=u.start,u.start=0))}],prefilter:function(e,t){t?tZ.prefilters.unshift(e):tZ.prefilters.push(e)}}),T.speed=function(e,t,n){var r=e&&"object"==typeof e?T.extend({},e):{complete:n||t||"function"==typeof e&&e,duration:e,easing:n&&t||t&&"function"!=typeof t&&t};return T.fx.off?r.duration=0:"number"!=typeof r.duration&&(r.duration in T.fx.speeds?r.duration=T.fx.speeds[r.duration]:r.duration=T.fx.speeds._default),(null==r.queue||!0===r.queue)&&(r.queue="fx"),r.old=r.complete,r.complete=function(){"function"==typeof r.old&&r.old.call(this),r.queue&&T.dequeue(this,r.queue)},r},T.fn.extend({fadeTo:function(e,t,n,r){return this.filter(e8).css("opacity",0).show().end().animate({opacity:t},e,n,r)},animate:function(e,t,n,r){var i=T.isEmptyObject(e),o=T.speed(t,n,r),a=function(){var t=tZ(this,T.extend({},e),o);(i||e0.get(this,"finish"))&&t.stop(!0)};return a.finish=a,i||!1===o.queue?this.each(a):this.queue(o.queue,a)},stop:function(e,t,n){var r=function(e){var t=e.stop;delete e.stop,t(n)};return"string"!=typeof e&&(n=t,t=e,e=void 0),t&&this.queue(e||"fx",[]),this.each(function(){var t=!0,i=null!=e&&e+"queueHooks",o=T.timers,a=e0.get(this);if(i)a[i]&&a[i].stop&&r(a[i]);else for(i in a)a[i]&&a[i].stop&&tV.test(i)&&r(a[i]);for(i=o.length;i--;)o[i].elem===this&&(null==e||o[i].queue===e)&&(o[i].anim.stop(n),t=!1,o.splice(i,1));(t||!n)&&T.dequeue(this,e)})},finish:function(e){return!1!==e&&(e=e||"fx"),this.each(function(){var t,n=e0.get(this),r=n[e+"queue"],i=n[e+"queueHooks"],o=T.timers,a=r?r.length:0;for(n.finish=!0,T.queue(this,e,[]),i&&i.stop&&i.stop.call(this,!0),t=o.length;t--;)o[t].elem===this&&o[t].queue===e&&(o[t].anim.stop(!0),o.splice(t,1));for(t=0;t<a;t++)r[t]&&r[t].finish&&r[t].finish.call(this);delete n.finish})}}),T.each(["toggle","show","hide"],function(e,t){var n=T.fn[t];T.fn[t]=function(e,r,i){return null==e||"boolean"==typeof e?n.apply(this,arguments):this.animate(tJ(t,!0),e,r,i)}}),T.each({slideDown:tJ("show"),slideUp:tJ("hide"),slideToggle:tJ("toggle"),fadeIn:{opacity:"show"},fadeOut:{opacity:"hide"},fadeToggle:{opacity:"toggle"}},function(e,t){T.fn[e]=function(e,n,r){return this.animate(t,e,n,r)}}),T.timers=[],T.fx.tick=function(){var e,t=0,n=T.timers;for(tz=Date.now();t<n.length;t++)(e=n[t])()||n[t]!==e||n.splice(t--,1);n.length||T.fx.stop(),tz=void 0},T.fx.timer=function(e){T.timers.push(e),T.fx.start()},T.fx.start=function(){tY||(tY=!0,function t(){tY&&(!1===y.hidden&&e.requestAnimationFrame?e.requestAnimationFrame(t):e.setTimeout(t,13),T.fx.tick())}())},T.fx.stop=function(){tY=null},T.fx.speeds={slow:600,fast:200,_default:400},T.fn.delay=function(t,n){return t=T.fx&&T.fx.speeds[t]||t,n=n||"fx",this.queue(n,function(n,r){var i=e.setTimeout(n,t);r.stop=function(){e.clearTimeout(i)}})};var t0=/^(?:input|select|textarea|button)$/i,t1=/^(?:a|area)$/i;function t2(e){return(e.match(Q)||[]).join(" ")}function t3(e){return e.getAttribute&&e.getAttribute("class")||""}function t4(e){return Array.isArray(e)?e:"string"==typeof e&&e.match(Q)||[]}T.fn.extend({prop:function(e,t){return V(this,T.prop,e,t,arguments.length>1)},removeProp:function(e){return this.each(function(){delete this[T.propFix[e]||e]})}}),T.extend({prop:function(e,t,n){var r,i,o=e.nodeType;if(3!==o&&8!==o&&2!==o)return(1===o&&T.isXMLDoc(e)||(t=T.propFix[t]||t,i=T.propHooks[t]),void 0!==n)?i&&"set"in i&&void 0!==(r=i.set(e,n,t))?r:e[t]=n:i&&"get"in i&&null!==(r=i.get(e,t))?r:e[t]},propHooks:{tabIndex:{get:function(e){var t=e.getAttribute("tabindex");return t?parseInt(t,10):t0.test(e.nodeName)||t1.test(e.nodeName)&&e.href?0:-1}}},propFix:{for:"htmlFor",class:"className"}}),k&&(T.propHooks.selected={get:function(e){var t=e.parentNode;return t&&t.parentNode&&t.parentNode.selectedIndex,null},set:function(e){var t=e.parentNode;t&&(t.selectedIndex,t.parentNode&&t.parentNode.selectedIndex)}}),T.each(["tabIndex","readOnly","maxLength","cellSpacing","cellPadding","rowSpan","colSpan","useMap","frameBorder","contentEditable"],function(){T.propFix[this.toLowerCase()]=this}),T.fn.extend({addClass:function(e){var t,n,r,i,o,a;return"function"==typeof e?this.each(function(t){T(this).addClass(e.call(this,t,t3(this)))}):(t=t4(e)).length?this.each(function(){if(r=t3(this),n=1===this.nodeType&&" "+t2(r)+" "){for(o=0;o<t.length;o++)i=t[o],0>n.indexOf(" "+i+" ")&&(n+=i+" ");r!==(a=t2(n))&&this.setAttribute("class",a)}}):this},removeClass:function(e){var t,n,r,i,o,a;return"function"==typeof e?this.each(function(t){T(this).removeClass(e.call(this,t,t3(this)))}):arguments.length?(t=t4(e)).length?this.each(function(){if(r=t3(this),n=1===this.nodeType&&" "+t2(r)+" "){for(o=0;o<t.length;o++){i=t[o];while(n.indexOf(" "+i+" ")>-1)n=n.replace(" "+i+" "," ")}r!==(a=t2(n))&&this.setAttribute("class",a)}}):this:this.attr("class","")},toggleClass:function(e,t){var n,r,i,o;return"function"==typeof e?this.each(function(n){T(this).toggleClass(e.call(this,n,t3(this),t),t)}):"boolean"==typeof t?t?this.addClass(e):this.removeClass(e):(n=t4(e)).length?this.each(function(){for(i=0,o=T(this);i<n.length;i++)r=n[i],o.hasClass(r)?o.removeClass(r):o.addClass(r)}):this},hasClass:function(e){var t,n,r=0;t=" "+e+" ";while(n=this[r++])if(1===n.nodeType&&(" "+t2(t3(n))+" ").indexOf(t)>-1)return!0;return!1}}),T.fn.extend({val:function(e){var t,n,r,i=this[0];if(!arguments.length)return i?(t=T.valHooks[i.type]||T.valHooks[i.nodeName.toLowerCase()])&&"get"in t&&void 0!==(n=t.get(i,"value"))?n:null==(n=i.value)?"":n:void 0;return r="function"==typeof e,this.each(function(n){var i;1===this.nodeType&&(null==(i=r?e.call(this,n,T(this).val()):e)?i="":"number"==typeof i?i+="":Array.isArray(i)&&(i=T.map(i,function(e){return null==e?"":e+""})),(t=T.valHooks[this.type]||T.valHooks[this.nodeName.toLowerCase()])&&"set"in t&&void 0!==t.set(this,i,"value")||(this.value=i))})}}),T.extend({valHooks:{select:{get:function(e){var t,n,r,i=e.options,o=e.selectedIndex,a="select-one"===e.type,s=a?null:[],u=a?o+1:i.length;for(r=o<0?u:a?o:0;r<u;r++)if((n=i[r]).selected&&!n.disabled&&(!n.parentNode.disabled||!C(n.parentNode,"optgroup"))){if(t=T(n).val(),a)return t;s.push(t)}return s},set:function(e,t){var n,r,i=e.options,o=T.makeArray(t),a=i.length;while(a--)((r=i[a]).selected=T.inArray(T(r).val(),o)>-1)&&(n=!0);return n||(e.selectedIndex=-1),o}}}}),k&&(T.valHooks.option={get:function(e){var t=e.getAttribute("value");return null!=t?t:t2(T.text(e))}}),T.each(["radio","checkbox"],function(){T.valHooks[this]={set:function(e,t){if(Array.isArray(t))return e.checked=T.inArray(T(e).val(),t)>-1}}});var t5=/^(?:focusinfocus|focusoutblur)$/,t9=function(e){e.stopPropagation()};T.extend(T.event,{trigger:function(t,n,r,i){var o,a,s,u,l,f,p,d,h=[r||y],v=c.call(t,"type")?t.type:t,m=c.call(t,"namespace")?t.namespace.split("."):[];if((a=d=s=r=r||y,!(3===r.nodeType||8===r.nodeType||t5.test(v+T.event.triggered)))&&(v.indexOf(".")>-1&&(v=(m=v.split(".")).shift(),m.sort()),l=0>v.indexOf(":")&&"on"+v,(t=t[T.expando]?t:new T.Event(v,"object"==typeof t&&t)).isTrigger=i?2:3,t.namespace=m.join("."),t.rnamespace=t.namespace?RegExp("(^|\\.)"+m.join("\\.(?:.*\\.|)")+"(\\.|$)"):null,t.result=void 0,t.target||(t.target=r),n=null==n?[t]:T.makeArray(n,[t]),p=T.event.special[v]||{},i||!p.trigger||!1!==p.trigger.apply(r,n))){if(!i&&!p.noBubble&&!g(r)){for(u=p.delegateType||v,!t5.test(u+v)&&(a=a.parentNode);a;a=a.parentNode)h.push(a),s=a;s===(r.ownerDocument||y)&&h.push(s.defaultView||s.parentWindow||e)}o=0;while((a=h[o++])&&!t.isPropagationStopped())d=a,t.type=o>1?u:p.bindType||v,(f=(e0.get(a,"events")||Object.create(null))[t.type]&&e0.get(a,"handle"))&&f.apply(a,n),(f=l&&a[l])&&f.apply&&eK(a)&&(t.result=f.apply(a,n),!1===t.result&&t.preventDefault());return t.type=v,!i&&!t.isDefaultPrevented()&&(!p._default||!1===p._default.apply(h.pop(),n))&&eK(r)&&l&&"function"==typeof r[v]&&!g(r)&&((s=r[l])&&(r[l]=null),T.event.triggered=v,t.isPropagationStopped()&&d.addEventListener(v,t9),r[v](),t.isPropagationStopped()&&d.removeEventListener(v,t9),T.event.triggered=void 0,s&&(r[l]=s)),t.result}},simulate:function(e,t,n){var r=T.extend(new T.Event,n,{type:e,isSimulated:!0});T.event.trigger(r,null,t)}}),T.fn.extend({trigger:function(e,t){return this.each(function(){T.event.trigger(e,t,this)})},triggerHandler:function(e,t){var n=this[0];if(n)return T.event.trigger(e,t,n,!0)}});var t6=e.location,t8={guid:Date.now()},t7=/\?/;T.parseXML=function(t){var n,r;if(!t||"string"!=typeof t)return null;try{n=new e.DOMParser().parseFromString(t,"text/xml")}catch(e){}return r=n&&n.getElementsByTagName("parsererror")[0],(!n||r)&&T.error("Invalid XML: "+(r?T.map(r.childNodes,function(e){return e.textContent}).join("\n"):t)),n};var ne=/\[\]$/,nt=/\r?\n/g,nn=/^(?:submit|button|image|reset|file)$/i,nr=/^(?:input|select|textarea|keygen)/i;T.param=function(e,t){var n,r=[],i=function(e,t){var n="function"==typeof t?t():t;r[r.length]=encodeURIComponent(e)+"="+encodeURIComponent(null==n?"":n)};if(null==e)return"";if(Array.isArray(e)||e.jquery&&!T.isPlainObject(e))T.each(e,function(){i(this.name,this.value)});else for(n in e)!function e(t,n,r,i){var o;if(Array.isArray(n))T.each(n,function(n,o){r||ne.test(t)?i(t,o):e(t+"["+("object"==typeof o&&null!=o?n:"")+"]",o,r,i)});else if(r||"object"!==h(n))i(t,n);else for(o in n)e(t+"["+o+"]",n[o],r,i)}(n,e[n],t,i);return r.join("&")},T.fn.extend({serialize:function(){return T.param(this.serializeArray())},serializeArray:function(){return this.map(function(){var e=T.prop(this,"elements");return e?T.makeArray(e):this}).filter(function(){var e=this.type;return this.name&&!T(this).is(":disabled")&&nr.test(this.nodeName)&&!nn.test(e)&&(this.checked||!tx.test(e))}).map(function(e,t){var n=T(this).val();return null==n?null:Array.isArray(n)?T.map(n,function(e){return{name:t.name,value:e.replace(nt,"\r\n")}}):{name:t.name,value:n.replace(nt,"\r\n")}}).get()}});var ni=/%20/g,no=/#.*$/,na=/([?&])_=[^&]*/,ns=/^(.*?):[ \t]*([^\r\n]*)$/mg,nu=/^(?:GET|HEAD)$/,nl=/^\/\//,nc={},nf={},np="*/".concat("*"),nd=y.createElement("a");function nh(e){return function(t,n){"string"!=typeof t&&(n=t,t="*");var r,i=0,o=t.toLowerCase().match(Q)||[];if("function"==typeof n)while(r=o[i++])"+"===r[0]?(e[r=r.slice(1)||"*"]=e[r]||[]).unshift(n):(e[r]=e[r]||[]).push(n)}}function ng(e,t,n,r){var i={},o=e===nf;function a(s){var u;return i[s]=!0,T.each(e[s]||[],function(e,s){var l=s(t,n,r);return"string"!=typeof l||o||i[l]?o?!(u=l):void 0:(t.dataTypes.unshift(l),a(l),!1)}),u}return a(t.dataTypes[0])||!i["*"]&&a("*")}function nv(e,t){var n,r,i=T.ajaxSettings.flatOptions||{};for(n in t)void 0!==t[n]&&((i[n]?e:r||(r={}))[n]=t[n]);return r&&T.extend(!0,e,r),e}nd.href=t6.href,T.extend({active:0,lastModified:{},etag:{},ajaxSettings:{url:t6.href,type:"GET",isLocal:/^(?:about|app|app-storage|.+-extension|file|res|widget):$/.test(t6.protocol),global:!0,processData:!0,async:!0,contentType:"application/x-www-form-urlencoded; charset=UTF-8",accepts:{"*":np,text:"text/plain",html:"text/html",xml:"application/xml, text/xml",json:"application/json, text/javascript"},contents:{xml:/\bxml\b/,html:/\bhtml/,json:/\bjson\b/},responseFields:{xml:"responseXML",text:"responseText",json:"responseJSON"},converters:{"* text":String,"text html":!0,"text json":JSON.parse,"text xml":T.parseXML},flatOptions:{url:!0,context:!0}},ajaxSetup:function(e,t){return t?nv(nv(e,T.ajaxSettings),t):nv(T.ajaxSettings,e)},ajaxPrefilter:nh(nc),ajaxTransport:nh(nf),ajax:function(t,n){"object"==typeof t&&(n=t,t=void 0),n=n||{};var r,i,o,a,s,u,l,c,f,p,d=T.ajaxSetup({},n),h=d.context||d,g=d.context&&(h.nodeType||h.jquery)?T(h):T.event,v=T.Deferred(),m=T.Callbacks("once memory"),x=d.statusCode||{},b={},w={},C="canceled",j={readyState:0,getResponseHeader:function(e){var t;if(l){if(!a){a={};while(t=ns.exec(o))a[t[1].toLowerCase()+" "]=(a[t[1].toLowerCase()+" "]||[]).concat(t[2])}t=a[e.toLowerCase()+" "]}return null==t?null:t.join(", ")},getAllResponseHeaders:function(){return l?o:null},setRequestHeader:function(e,t){return null==l&&(b[e=w[e.toLowerCase()]=w[e.toLowerCase()]||e]=t),this},overrideMimeType:function(e){return null==l&&(d.mimeType=e),this},statusCode:function(e){var t;if(e)if(l)j.always(e[j.status]);else for(t in e)x[t]=[x[t],e[t]];return this},abort:function(e){var t=e||C;return r&&r.abort(t),E(0,t),this}};if(v.promise(j),d.url=((t||d.url||t6.href)+"").replace(nl,t6.protocol+"//"),d.type=n.method||n.type||d.method||d.type,d.dataTypes=(d.dataType||"*").toLowerCase().match(Q)||[""],null==d.crossDomain){u=y.createElement("a");try{u.href=d.url,u.href=u.href,d.crossDomain=nd.protocol+"//"+nd.host!=u.protocol+"//"+u.host}catch(e){d.crossDomain=!0}}if(ng(nc,d,n,j),d.data&&d.processData&&"string"!=typeof d.data&&(d.data=T.param(d.data,d.traditional)),l)return j;for(f in(c=T.event&&d.global)&&0==T.active++&&T.event.trigger("ajaxStart"),d.type=d.type.toUpperCase(),d.hasContent=!nu.test(d.type),i=d.url.replace(no,""),d.hasContent?d.data&&d.processData&&0===(d.contentType||"").indexOf("application/x-www-form-urlencoded")&&(d.data=d.data.replace(ni,"+")):(p=d.url.slice(i.length),d.data&&(d.processData||"string"==typeof d.data)&&(i+=(t7.test(i)?"&":"?")+d.data,delete d.data),!1===d.cache&&(i=i.replace(na,"$1"),p=(t7.test(i)?"&":"?")+"_="+t8.guid+++p),d.url=i+p),d.ifModified&&(T.lastModified[i]&&j.setRequestHeader("If-Modified-Since",T.lastModified[i]),T.etag[i]&&j.setRequestHeader("If-None-Match",T.etag[i])),(d.data&&d.hasContent&&!1!==d.contentType||n.contentType)&&j.setRequestHeader("Content-Type",d.contentType),j.setRequestHeader("Accept",d.dataTypes[0]&&d.accepts[d.dataTypes[0]]?d.accepts[d.dataTypes[0]]+("*"!==d.dataTypes[0]?", "+np+"; q=0.01":""):d.accepts["*"]),d.headers)j.setRequestHeader(f,d.headers[f]);if(d.beforeSend&&(!1===d.beforeSend.call(h,j,d)||l))return j.abort();if(C="abort",m.add(d.complete),j.done(d.success),j.fail(d.error),r=ng(nf,d,n,j)){if(j.readyState=1,c&&g.trigger("ajaxSend",[j,d]),l)return j;d.async&&d.timeout>0&&(s=e.setTimeout(function(){j.abort("timeout")},d.timeout));try{l=!1,r.send(b,E)}catch(e){if(l)throw e;E(-1,e)}}else E(-1,"No Transport");function E(t,n,a,u){var f,p,y,b,w,C=n;!l&&(l=!0,s&&e.clearTimeout(s),r=void 0,o=u||"",j.readyState=4*(t>0),f=t>=200&&t<300||304===t,a&&(b=function(e,t,n){var r,i,o,a,s=e.contents,u=e.dataTypes;while("*"===u[0])u.shift(),void 0===r&&(r=e.mimeType||t.getResponseHeader("Content-Type"));if(r){for(i in s)if(s[i]&&s[i].test(r)){u.unshift(i);break}}if(u[0]in n)o=u[0];else{for(i in n){if(!u[0]||e.converters[i+" "+u[0]]){o=i;break}a||(a=i)}o=o||a}if(o)return o!==u[0]&&u.unshift(o),n[o]}(d,j,a)),!f&&T.inArray("script",d.dataTypes)>-1&&0>T.inArray("json",d.dataTypes)&&(d.converters["text script"]=function(){}),b=function(e,t,n,r){var i,o,a,s,u,l={},c=e.dataTypes.slice();if(c[1])for(a in e.converters)l[a.toLowerCase()]=e.converters[a];o=c.shift();while(o)if(e.responseFields[o]&&(n[e.responseFields[o]]=t),!u&&r&&e.dataFilter&&(t=e.dataFilter(t,e.dataType)),u=o,o=c.shift()){if("*"===o)o=u;else if("*"!==u&&u!==o){if(!(a=l[u+" "+o]||l["* "+o])){for(i in l)if((s=i.split(" "))[1]===o&&(a=l[u+" "+s[0]]||l["* "+s[0]])){!0===a?a=l[i]:!0!==l[i]&&(o=s[0],c.unshift(s[1]));break}}if(!0!==a)if(a&&e.throws)t=a(t);else try{t=a(t)}catch(e){return{state:"parsererror",error:a?e:"No conversion from "+u+" to "+o}}}}return{state:"success",data:t}}(d,b,j,f),f?(d.ifModified&&((w=j.getResponseHeader("Last-Modified"))&&(T.lastModified[i]=w),(w=j.getResponseHeader("etag"))&&(T.etag[i]=w)),204===t||"HEAD"===d.type?C="nocontent":304===t?C="notmodified":(C=b.state,p=b.data,f=!(y=b.error))):(y=C,(t||!C)&&(C="error",t<0&&(t=0))),j.status=t,j.statusText=(n||C)+"",f?v.resolveWith(h,[p,C,j]):v.rejectWith(h,[j,C,y]),j.statusCode(x),x=void 0,c&&g.trigger(f?"ajaxSuccess":"ajaxError",[j,d,f?p:y]),m.fireWith(h,[j,C]),c&&(g.trigger("ajaxComplete",[j,d]),--T.active||T.event.trigger("ajaxStop")))}return j},getJSON:function(e,t,n){return T.get(e,t,n,"json")},getScript:function(e,t){return T.get(e,void 0,t,"script")}}),T.each(["get","post"],function(e,t){T[t]=function(e,n,r,i){return("function"==typeof n||null===n)&&(i=i||r,r=n,n=void 0),T.ajax(T.extend({url:e,type:t,dataType:i,data:n,success:r},T.isPlainObject(e)&&e))}}),T.ajaxPrefilter(function(e){var t;for(t in e.headers)"content-type"===t.toLowerCase()&&(e.contentType=e.headers[t]||"")}),T._evalUrl=function(e,t,n){return T.ajax({url:e,type:"GET",dataType:"script",cache:!0,async:!1,global:!1,scriptAttrs:t.crossOrigin?{crossOrigin:t.crossOrigin}:void 0,converters:{"text script":function(){}},dataFilter:function(e){T.globalEval(e,t,n)}})},T.fn.extend({wrapAll:function(e){var t;return this[0]&&("function"==typeof e&&(e=e.call(this[0])),t=T(e,this[0].ownerDocument).eq(0).clone(!0),this[0].parentNode&&t.insertBefore(this[0]),t.map(function(){var e=this;while(e.firstElementChild)e=e.firstElementChild;return e}).append(this)),this},wrapInner:function(e){return"function"==typeof e?this.each(function(t){T(this).wrapInner(e.call(this,t))}):this.each(function(){var t=T(this),n=t.contents();n.length?n.wrapAll(e):t.append(e)})},wrap:function(e){var t="function"==typeof e;return this.each(function(n){T(this).wrapAll(t?e.call(this,n):e)})},unwrap:function(e){return this.parent(e).not("body").each(function(){T(this).replaceWith(this.childNodes)}),this}}),T.expr.pseudos.hidden=function(e){return!T.expr.pseudos.visible(e)},T.expr.pseudos.visible=function(e){return!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length)},T.ajaxSettings.xhr=function(){return new e.XMLHttpRequest};var ny={0:200};function nm(e){return e.scriptAttrs||!e.headers&&(e.crossDomain||e.async&&0>T.inArray("json",e.dataTypes))}T.ajaxTransport(function(e){var t;return{send:function(n,r){var i,o=e.xhr();if(o.open(e.type,e.url,e.async,e.username,e.password),e.xhrFields)for(i in e.xhrFields)o[i]=e.xhrFields[i];for(i in e.mimeType&&o.overrideMimeType&&o.overrideMimeType(e.mimeType),e.crossDomain||n["X-Requested-With"]||(n["X-Requested-With"]="XMLHttpRequest"),n)o.setRequestHeader(i,n[i]);t=function(e){return function(){t&&(t=o.onload=o.onerror=o.onabort=o.ontimeout=null,"abort"===e?o.abort():"error"===e?r(o.status,o.statusText):r(ny[o.status]||o.status,o.statusText,"text"===(o.responseType||"text")?{text:o.responseText}:{binary:o.response},o.getAllResponseHeaders()))}},o.onload=t(),o.onabort=o.onerror=o.ontimeout=t("error"),t=t("abort");try{o.send(e.hasContent&&e.data||null)}catch(e){if(t)throw e}},abort:function(){t&&t()}}}),T.ajaxSetup({accepts:{script:"text/javascript, application/javascript, application/ecmascript, application/x-ecmascript"},converters:{"text script":function(e){return T.globalEval(e),e}}}),T.ajaxPrefilter("script",function(e){void 0===e.cache&&(e.cache=!1),nm(e)&&(e.type="GET")}),T.ajaxTransport("script",function(e){if(nm(e)){var t,n;return{send:function(r,i){t=T("<script>").attr(e.scriptAttrs||{}).prop({charset:e.scriptCharset,src:e.url}).on("load error",n=function(e){t.remove(),n=null,e&&i("error"===e.type?404:200,e.type)}),y.head.appendChild(t[0])},abort:function(){n&&n()}}}});var nx=[],nb=/(=)\?(?=&|$)|\?\?/;T.ajaxSetup({jsonp:"callback",jsonpCallback:function(){var e=nx.pop()||T.expando+"_"+t8.guid++;return this[e]=!0,e}}),T.ajaxPrefilter("jsonp",function(t,n,r){var i,o,a,s=!1!==t.jsonp&&(nb.test(t.url)?"url":"string"==typeof t.data&&0===(t.contentType||"").indexOf("application/x-www-form-urlencoded")&&nb.test(t.data)&&"data");return i=t.jsonpCallback="function"==typeof t.jsonpCallback?t.jsonpCallback():t.jsonpCallback,s?t[s]=t[s].replace(nb,"$1"+i):!1!==t.jsonp&&(t.url+=(t7.test(t.url)?"&":"?")+t.jsonp+"="+i),t.converters["script json"]=function(){return a||T.error(i+" was not called"),a[0]},t.dataTypes[0]="json",o=e[i],e[i]=function(){a=arguments},r.always(function(){void 0===o?T(e).removeProp(i):e[i]=o,t[i]&&(t.jsonpCallback=n.jsonpCallback,nx.push(i)),a&&"function"==typeof o&&o(a[0]),a=o=void 0}),"script"}),T.ajaxPrefilter(function(t,n){"string"==typeof t.data||T.isPlainObject(t.data)||Array.isArray(t.data)||"processData"in n||(t.processData=!1),t.data instanceof e.FormData&&(t.contentType=!1)}),T.parseHTML=function(t,n,r){var i,o;return"string"==typeof t||eR(t+"")?("boolean"==typeof n&&(r=n,n=!1),n||(n=new e.DOMParser().parseFromString("","text/html")),i=eP.exec(t),o=!r&&[],i)?[n.createElement(i[1])]:(i=tg([t],n,o),o&&o.length&&T(o).remove(),T.merge([],i.childNodes)):[]},T.fn.load=function(e,t,n){var r,i,o,a=this,s=e.indexOf(" ");return s>-1&&(r=t2(e.slice(s)),e=e.slice(0,s)),"function"==typeof t?(n=t,t=void 0):t&&"object"==typeof t&&(i="POST"),a.length>0&&T.ajax({url:e,type:i||"GET",dataType:"html",data:t}).done(function(e){o=arguments,a.html(r?T("<div>").append(T.parseHTML(e)).find(r):e)}).always(n&&function(e,t){a.each(function(){n.apply(this,o||[e.responseText,t,e])})}),this},T.expr.pseudos.animated=function(e){return T.grep(T.timers,function(t){return e===t.elem}).length},T.offset={setOffset:function(e,t,n){var r,i,o,a,s,u,l=T.css(e,"position"),c=T(e),f={};"static"===l&&(e.style.position="relative"),s=c.offset(),o=T.css(e,"top"),u=T.css(e,"left"),("absolute"===l||"fixed"===l)&&(o+u).indexOf("auto")>-1?(a=(r=c.position()).top,i=r.left):(a=parseFloat(o)||0,i=parseFloat(u)||0),"function"==typeof t&&(t=t.call(e,n,T.extend({},s))),null!=t.top&&(f.top=t.top-s.top+a),null!=t.left&&(f.left=t.left-s.left+i),"using"in t?t.using.call(e,f):c.css(f)}},T.fn.extend({offset:function(e){if(arguments.length)return void 0===e?this:this.each(function(t){T.offset.setOffset(this,e,t)});var t,n,r=this[0];if(r)return r.getClientRects().length?(t=r.getBoundingClientRect(),n=r.ownerDocument.defaultView,{top:t.top+n.pageYOffset,left:t.left+n.pageXOffset}):{top:0,left:0}},position:function(){if(this[0]){var e,t,n,r=this[0],i={top:0,left:0};if("fixed"===T.css(r,"position"))t=r.getBoundingClientRect();else{t=this.offset(),n=r.ownerDocument,e=r.offsetParent||n.documentElement;while(e&&e!==n.documentElement&&"static"===T.css(e,"position"))e=e.offsetParent||n.documentElement;e&&e!==r&&1===e.nodeType&&"static"!==T.css(e,"position")&&(i=T(e).offset(),i.top+=T.css(e,"borderTopWidth",!0),i.left+=T.css(e,"borderLeftWidth",!0))}return{top:t.top-i.top-T.css(r,"marginTop",!0),left:t.left-i.left-T.css(r,"marginLeft",!0)}}},offsetParent:function(){return this.map(function(){var e=this.offsetParent;while(e&&"static"===T.css(e,"position"))e=e.offsetParent;return e||L})}}),T.each({scrollLeft:"pageXOffset",scrollTop:"pageYOffset"},function(e,t){var n="pageYOffset"===t;T.fn[e]=function(r){return V(this,function(e,r,i){var o;if(g(e)?o=e:9===e.nodeType&&(o=e.defaultView),void 0===i)return o?o[t]:e[r];o?o.scrollTo(n?o.pageXOffset:i,n?i:o.pageYOffset):e[r]=i},e,r,arguments.length)}}),T.each({Height:"height",Width:"width"},function(e,t){T.each({padding:"inner"+e,content:t,"":"outer"+e},function(n,r){T.fn[r]=function(i,o){var a=arguments.length&&(n||"boolean"!=typeof i),s=n||(!0===i||!0===o?"margin":"border");return V(this,function(t,n,i){var o;return g(t)?0===r.indexOf("outer")?t["inner"+e]:t.document.documentElement["client"+e]:9===t.nodeType?(o=t.documentElement,Math.max(t.body["scroll"+e],o["scroll"+e],t.body["offset"+e],o["offset"+e],o["client"+e])):void 0===i?T.css(t,n,s):T.style(t,n,i,s)},t,a?i:void 0,a)}})}),T.each(["ajaxStart","ajaxStop","ajaxComplete","ajaxError","ajaxSuccess","ajaxSend"],function(e,t){T.fn[t]=function(e){return this.on(t,e)}}),T.fn.extend({bind:function(e,t,n){return this.on(e,null,t,n)},unbind:function(e,t){return this.off(e,null,t)},delegate:function(e,t,n,r){return this.on(t,e,n,r)},undelegate:function(e,t,n){return 1==arguments.length?this.off(e,"**"):this.off(t,e||"**",n)},hover:function(e,t){return this.on("mouseenter",e).on("mouseleave",t||e)}}),T.each("blur focus focusin focusout resize scroll click dblclick mousedown mouseup mousemove mouseover mouseout mouseenter mouseleave change select submit keydown keypress keyup contextmenu".split(" "),function(e,t){T.fn[t]=function(e,n){return arguments.length>0?this.on(t,null,e,n):this.trigger(t)}}),T.proxy=function(e,t){var n,r,o;if("string"==typeof t&&(n=e[t],t=e,e=n),"function"==typeof e)return r=i.call(arguments,2),(o=function(){return e.apply(t||this,r.concat(i.call(arguments)))}).guid=e.guid=e.guid||T.guid++,o},T.holdReady=function(e){e?T.readyWait++:T.ready(!0)},T.expr[":"]=T.expr.filters=T.expr.pseudos,"function"==typeof define&&define.amd&&define("jquery",[],function(){return T});var nw=e.jQuery,nT=e.$;return T.noConflict=function(t){return e.$===T&&(e.$=nT),t&&e.jQuery===T&&(e.jQuery=nw),T},void 0===t&&(e.jQuery=e.$=T),T});

const isIOS =
  /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

if (isIOS) {
  document.documentElement.classList.add('ios');
}

if (window.NodeList && !NodeList.prototype.forEach) {
    NodeList.prototype.forEach = function (callback, thisArg) {
        thisArg = thisArg || window;
        for (var i = 0; i < this.length; i++) {
            callback.call(thisArg, this[i], i, this);
        }
    };
}

(function () {
    if (typeof Element === "undefined" || Element.prototype.replaceChildren)
        return;
    function replaceChildren() {
        while (this.firstChild)
            this.removeChild(this.firstChild);
        for (var i = 0; i < arguments.length; i++) {
            var node = arguments[i];
            if (typeof node === "string")
                node = document.createTextNode(node);
            this.appendChild(node);
        }
    }
    Element.prototype.replaceChildren = replaceChildren;
    if (typeof Document !== "undefined" && !Document.prototype.replaceChildren)
        Document.prototype.replaceChildren = replaceChildren;
    if (typeof DocumentFragment !== "undefined" && !DocumentFragment.prototype.replaceChildren)
        DocumentFragment.prototype.replaceChildren = replaceChildren;
})();


// @codekit-prepend "node_modules/jquery/dist/jquery.min.js"
// @codekit-prepend "script/polyfill-ios.js"
// @codekit-prepend "script/polyfill-foreach.js"
// @codekit-prepend "script/polyfill-replacechildren.js"
// @codekit-append "node_modules/autosize/dist/autosize.min.js"

// @codekit-append "script/diff-min.js"
// @codekit-append "script/whirlcode2-min.js"
// @codekit-append "script/whirlcode3-min.js"
// @codekit-append "script/wp-topbar-min.js"
// @codekit-append "script/wp-forum-threads-min.js"
// @codekit-append "script/wp-forum-replies-min.js"
// @codekit-append "script/wp-forum-reply-min.js"
// @codekit-append "script/wp-checkbox-range-min.js"


!function(e,t){"object"==typeof exports&&"undefined"!=typeof module?module.exports=t():"function"==typeof define&&define.amd?define(t):(e||self).autosize=t()}(this,function(){var e=new Map;function t(t){var o=e.get(t);o&&o.destroy()}function o(t){var o=e.get(t);o&&o.update()}var r=null;return"undefined"==typeof window?((r=function(e){return e}).destroy=function(e){return e},r.update=function(e){return e}):((r=function(t,o){return t&&Array.prototype.forEach.call(t.length?t:[t],function(t){return function(t){if(t&&t.nodeName&&"TEXTAREA"===t.nodeName&&!e.has(t)){var o,r=null,n=window.getComputedStyle(t),i=(o=t.value,function(){s({testForHeightReduction:""===o||!t.value.startsWith(o),restoreTextAlign:null}),o=t.value}),l=function(o){t.removeEventListener("autosize:destroy",l),t.removeEventListener("autosize:update",a),t.removeEventListener("input",i),window.removeEventListener("resize",a),Object.keys(o).forEach(function(e){return t.style[e]=o[e]}),e.delete(t)}.bind(t,{height:t.style.height,resize:t.style.resize,textAlign:t.style.textAlign,overflowY:t.style.overflowY,overflowX:t.style.overflowX,wordWrap:t.style.wordWrap});t.addEventListener("autosize:destroy",l),t.addEventListener("autosize:update",a),t.addEventListener("input",i),window.addEventListener("resize",a),t.style.overflowX="hidden",t.style.wordWrap="break-word",e.set(t,{destroy:l,update:a}),a()}function s(e){var o,i,l=e.restoreTextAlign,a=void 0===l?null:l,d=e.testForHeightReduction,u=void 0===d||d,f=n.overflowY;if(0!==t.scrollHeight&&("vertical"===n.resize?t.style.resize="none":"both"===n.resize&&(t.style.resize="horizontal"),u&&(o=function(e){for(var t=[];e&&e.parentNode&&e.parentNode instanceof Element;)e.parentNode.scrollTop&&t.push([e.parentNode,e.parentNode.scrollTop]),e=e.parentNode;return function(){return t.forEach(function(e){var t=e[0],o=e[1];t.style.scrollBehavior="auto",t.scrollTop=o,t.style.scrollBehavior=null})}}(t),t.style.height=""),i="content-box"===n.boxSizing?t.scrollHeight-(parseFloat(n.paddingTop)+parseFloat(n.paddingBottom)):t.scrollHeight+parseFloat(n.borderTopWidth)+parseFloat(n.borderBottomWidth),"none"!==n.maxHeight&&i>parseFloat(n.maxHeight)?("hidden"===n.overflowY&&(t.style.overflow="scroll"),i=parseFloat(n.maxHeight)):"hidden"!==n.overflowY&&(t.style.overflow="hidden"),t.style.height=i+"px",a&&(t.style.textAlign=a),o&&o(),r!==i&&(t.dispatchEvent(new Event("autosize:resized",{bubbles:!0})),r=i),f!==n.overflow&&!a)){var c=n.textAlign;"hidden"===n.overflow&&(t.style.textAlign="start"===c?"end":"start"),s({restoreTextAlign:c,testForHeightReduction:!0})}}function a(){s({testForHeightReduction:!0,restoreTextAlign:null})}}(t)}),t}).destroy=function(e){return e&&Array.prototype.forEach.call(e.length?e:[e],t),e},r.update=function(e){return e&&Array.prototype.forEach.call(e.length?e:[e],o),e}),r});


function escape(s){var n=s
;return n=(n=(n=(n=n.replace(/&/g,"&amp;")).replace(/</g,"&lt;")).replace(/>/g,"&gt;")).replace(/"/g,"&quot;")
}function diffString(o,n){o=o.replace(/\s+$/,"");n=n.replace(/\s+$/,"")
;var out=diff(""==o?[]:o.split(/\s+/),""==n?[]:n.split(/\s+/));var str="";var oSpace=o.match(/\s+/g)
;if(null==oSpace)oSpace=["\n"];else oSpace.push("\n");var nSpace=n.match(/\s+/g)
;if(null==nSpace)nSpace=["\n"];else nSpace.push("\n")
;if(0==out.n.length)for(var i=0;i<out.o.length;i++)str+="<del>"+escape(out.o[i])+oSpace[i]+"</del>";else{
if(null==out.n[0].text)for(n=0;n<out.o.length&&null==out.o[n].text;n++)str+="<del>"+escape(out.o[n])+oSpace[n]+"</del>"
;for(var i=0;i<out.n.length;i++)if(null==out.n[i].text)str+="<ins>"+escape(out.n[i])+nSpace[i]+"</ins>";else{
var pre=""
;for(n=out.n[i].row+1;n<out.o.length&&null==out.o[n].text;n++)pre+="<del>"+escape(out.o[n])+oSpace[n]+"</del>"
;str+=" "+out.n[i].text+nSpace[i]+pre}}return str}function randomColor(){
return"rgb("+100*Math.random()+"%, "+100*Math.random()+"%, "+100*Math.random()+"%)"}
function diffString2(o,n){o=o.replace(/\s+$/,"");n=n.replace(/\s+$/,"")
;var out=diff(""==o?[]:o.split(/\s+/),""==n?[]:n.split(/\s+/));var oSpace=o.match(/\s+/g)
;if(null==oSpace)oSpace=["\n"];else oSpace.push("\n");var nSpace=n.match(/\s+/g)
;if(null==nSpace)nSpace=["\n"];else nSpace.push("\n");var os="";var colors=new Array
;for(var i=0;i<out.o.length;i++){colors[i]=randomColor()
;if(null!=out.o[i].text)os+='<span style="background-color: '+colors[i]+'">'+escape(out.o[i].text)+oSpace[i]+"</span>";else os+="<del>"+escape(out.o[i])+oSpace[i]+"</del>"
}var ns=""
;for(var i=0;i<out.n.length;i++)if(null!=out.n[i].text)ns+='<span style="background-color: '+colors[out.n[i].row]+'">'+escape(out.n[i].text)+nSpace[i]+"</span>";else ns+="<ins>"+escape(out.n[i])+nSpace[i]+"</ins>"
;return{o:os,n:ns}}function diff(o,n){var ns=new Object;var os=new Object
;for(var i=0;i<n.length;i++){if(!ns.hasOwnProperty(n[i]))ns[n[i]]={rows:new Array,o:null}
;ns[n[i]].rows.push(i)}for(var i=0;i<o.length;i++){if(!os.hasOwnProperty(o[i]))os[o[i]]={
rows:new Array,n:null};os[o[i]].rows.push(i)}
for(var i in ns)if(1==ns[i].rows.length&&"undefined"!=typeof os[i]&&1==os[i].rows.length){
n[ns[i].rows[0]]={text:n[ns[i].rows[0]],row:os[i].rows[0]};o[os[i].rows[0]]={text:o[os[i].rows[0]],
row:ns[i].rows[0]}}
for(var i=0;i<n.length-1;i++)if(null!=n[i].text&&null==n[i+1].text&&n[i].row+1<o.length&&null==o[n[i].row+1].text&&n[i+1]==o[n[i].row+1]){
n[i+1]={text:n[i+1],row:n[i].row+1};o[n[i].row+1]={text:o[n[i].row+1],row:i+1}}
for(var i=n.length-1;i>0;i--)if(null!=n[i].text&&null==n[i-1].text&&n[i].row>0&&null==o[n[i].row-1].text&&n[i-1]==o[n[i].row-1]){
n[i-1]={text:n[i-1],row:n[i].row-1};o[n[i].row-1]={text:o[n[i].row-1],row:i-1}}return{o:o,n:n}}

/*! 
Whirlcode parser | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
function whirlcode2(src,params){var txt=src;var i=0;enabled={whirlcode:true,headings:false,
paragraph:true,entities:true,basichtml:true,autolink:true,wordfilter:true,linelimit:true,
metablocks:false};if("string"==typeof params)enabled={whirlcode:params.indexOf("w")>-1,
headings:params.indexOf("h")>-1,paragraph:params.indexOf("p")>-1,entities:params.indexOf("e")>-1,
basichtml:params.indexOf("b")>-1,autolink:params.indexOf("a")>-1,wordfilter:params.indexOf("f")>-1,
linelimit:params.indexOf("l")>-1,metablocks:params.indexOf("m")>-1
};else if("object"==typeof params)for(i in params)enabled[i]=params[i]
;if(enabled.whirlcode)txt=txt.replace(/\[[\u2018\u2019]/g,"['").replace(/\[[\u201C\u201D]/g,'["').replace(/[\u2018\u2019]\]/g,"']").replace(/[\u201C\u201D]\]/g,'"]').replace(/=[\u201C\u201D]/g,'="').replace(/[\u201C\u201D]>/g,'">')
;if(enabled.metablocks)txt=txt.replace(/\{\{[\s\S]+\}\}/g,"")
;txt=(txt=(txt=txt.replace(/\u00AB/g,"&laquo;").replace(/\u00BB/g,"&raquo;").replace(/</g,"«").replace(/>/g,"»")).replace(/\r\n?/g,"\n").replace(/([^\n])\n([=#*]{1,3}\s|[+]{3}|[:$]\s|@[0-9]{1,12}\s|\+\+\{)/g,"$1\n\n$2").replace(/([^\n])\n(-----)/g,"$1\n\n$2").replace(/([^\n])\n([^\n])/g,"$1<br>$2").replace(/\n\n+/g,"\n")).replace(/\u00A0/g," ")
;if(enabled.whirlcode){
txt=(txt=txt.replace(/\[\[(([-A-Z0-9_ ]+?)\|(.+?)|([-A-Z0-9_ ]+?))\]\]/gi,'<a class="wiki" href="/wiki/$2$4">$3$4</a>')).replace(/"\/wiki\/([-A-Z0-9_ ]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?)"/gi,'"/wiki/$1_$2_$3_$4_$5"').replace(/"\/wiki\/([-A-Z0-9_ ]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?)"/gi,'"/wiki/$1_$2_$3_$4"').replace(/"\/wiki\/([-A-Z0-9_ ]*?) ([-A-Z0-9_]*?) ([-A-Z0-9_]*?)"/gi,'"/wiki/$1_$2_$3"').replace(/"\/wiki\/([-A-Z0-9_ ]*?) ([-A-Z0-9_]*?)"/gi,'"/wiki/$1_$2"')
;var whirlcode=[{code:"\\*",tag:"strong"},{code:"/",tag:"em"},{code:"\\\\",tag:"sub"},{code:"\\^",
tag:"sup"},{code:"-",tag:"strike"},{code:"#",tag:"tt"},{code:"\\(",tag:"small",code2:"\\)"},{
code:'"',tag:"span",plus:' class="wcrep1"'},{code:"'",tag:"span",plus:' class="wcrep2"'},{code:"`",
tag:"span",plus:' class="wcgrey"'},{code:"~",tag:"span",plus:' class="wcserif"'},{code:"_",
tag:"span",plus:' class="wcspoil"'},{code:"\\?",tag:"a",
plus:' href="http://www.google.com.au/search?q=$1"'}]
;for(i=0;i<whirlcode.length;i++)txt=txt.replace(new RegExp("\\["+whirlcode[i].code+"(.+?)"+(whirlcode[i].code2?whirlcode[i].code2:whirlcode[i].code)+"\\]","g"),"<"+whirlcode[i].tag+(whirlcode[i].plus?whirlcode[i].plus:"")+">$1</"+whirlcode[i].tag+">")
;txt=txt.replace(/\[\.(.+?)\.\]/g,"[$1]")}if(enabled.basichtml){var allowhtml=[{tag:"a",
inner:' href="(/|https?://|mailto:)[^"]+?"'},{tag:"strong|b"},{tag:"em|i"},{tag:"small"},{tag:"tt"
},{tag:"sup"},{tag:"sub"},{tag:"strike"}]
;for(i=0;i<allowhtml.length;i++)txt=txt.replace(new RegExp("«("+allowhtml[i].tag+")("+(allowhtml[i].inner?allowhtml[i].inner:"()")+")»(.+?)«/\\1»","ig"),"<"+allowhtml[i].tag.split("|")[0]+"$2>$4</"+allowhtml[i].tag.split("|")[0]+">")
}
if(enabled.entities)txt=txt.replace(/&(\s)/gi,"&amp;$1").replace(/ -- /gi," &mdash; ").replace(/ - /gi," &ndash; ").replace(/\(tm\)/gi,"&trade;").replace(/(\s)1\/4([^0-9])/gi,"$1&frac14;$2").replace(/(\s)1\/2([^0-9])/gi,"$1&frac12;$2").replace(/(\s)3\/4([^0-9])/gi,"$1&frac34;$2")
;if(enabled.wordfilter)txt=txt.replace(/fuck(ed|ing|er)/gi,"flapp$1").replace(/fuck/gi,"flap").replace(/cunt/gi,"legend")
;if(enabled.autolink)txt=(txt=(txt=(txt=" "+txt).replace(/([^""\/=])\b(https?:\/\/)?whrl\.pl\/([A-Za-z0-9]+)(?![^<]*<\/a>)/gi,'$1<a href="https://whrl.pl/$3">whrl.pl/$3</a>')).replace(/([^""\/=])\b(https?:\/\/)([-A-Z0-9+&@#\/%?=~_|!:,.;]*(?:_\([-A-Z0-9+&@#\/%=~_|?!:,.;]*\)|[-A-Z0-9+&@#\/%=~_|]))(?![^<]*<\/a>)/gi,'$1<a href="$2$3">$2$3</a>')).replace(/([^"":=])\b(?:mailto:)?([A-Z0-9._%-]+@[A-Z0-9.-]+\.[A-Z]{2,4})\b(?![^<]*<\/a>)/gi,'$1<a href="mailto:$2">$2</a>')
;var linein=txt.split("\n");var lines=[];var meta=[];for(i=0;i<linein.length;i++){
var l=linein[i].replace(/^\s+/,"").replace(/(\s|<br>)+$/,"");var m=""
;if(enabled.headings&&-1!=l.search(/^===\s+/))m="2"
;if(enabled.headings&&-1!=l.search(/^==\s+/))m="3";if(enabled.headings&&-1!=l.search(/^=\s+/))m="4"
;if(enabled.headings&&-1!=l.search(/^\+\+\{.+\}$/))m="+"
;if(enabled.paragraph&&-1!=l.search(/^:\s+/))m=":"
;if(enabled.paragraph&&-1!=l.search(/^\$\s+/))m="$"
;if(enabled.paragraph&&-1!=l.search(/^-----$/))m="-"
;if(enabled.paragraph&&-1!=l.search(/^@[0-9]{1,12}\s+/))m="@"
;if(enabled.paragraph&&-1!=l.search(/^[*#]{1,3}\s+/))m=l.match(/^([*#]{1,3})\s+/)[1]
;if(0==m.length&&l.length>0)m="p";switch(m.charAt(0)){case"2":
l="<h2>"+l.replace(/^===\s+/,"")+"</h2>";break;case"3":l="<h3>"+l.replace(/^==\s+/,"")+"</h3>";break
;case"4":l="<h4>"+l.replace(/^=\s+/,"")+"</h4>";break;case"p":l="<p>"+l+"</p>";break;case":":
l="<p>"+l.replace(/^:\s+/,"")+"</p>";break;case"@":
l='<p class="reference">'+l.replace(/^@([0-9]{1,12})\s+/,"$1 ").replace(/<br>/,"</p>\n<p>")+"</p>"
;break;case"#":case"*":l="<li>"+l.replace(/^[*#]{1,3}\s+/,"")+"</li>";break;case"$":
l=l.replace(/^\$\s+/,"");break;case"-":l="<hr>";break;case"+":
l='<p class="include" title="'+(l=l.replace(/^\+\+\{(.+)\}$/,"$1"))+'">(include: '+l+")</p>";break
;default:continue}var printable=l
;if(i<2)printable=printable.replace(/<span class="wcspoil">.{1,4}<\/span>/g,"")
;if(0===(printable=printable.replace(/<[^>]*>?/g,"").replace(/&nbsp;/g,"").replace(/\s+/g,"").replace(/[,.:;''`]{1,3}/,"")).length&&"-"!==m)continue
;lines.push(l);meta.push(m)}for(i=0;i<lines.length;i++)if(":"==meta[i]){
if(0==i||meta[i]!=meta[i-1])lines[i]="<blockquote>\n"+lines[i]
;if(i==lines.length-1||meta[i]!=meta[i+1])lines[i]+="\n</blockquote>"}else if("$"==meta[i]){
if(0==i||meta[i]!=meta[i-1])lines[i]="<pre>"+lines[i]
;if(i==lines.length-1||meta[i]!=meta[i+1])lines[i]+="</pre>"
}else if(meta[i].match(/(\*|#)+/))for(var j=2;j>=0;j--){var current=meta[i].charAt(j)
;if(current.length&&(0==i||current!=meta[i-1].charAt(j)))lines[i]=("*"==current?"<ul>\n":"<ol>\n")+lines[i]
;if(current.length&&(i==lines.length-1||current!=meta[i+1].charAt(j)))lines[i]+="*"==current?"\n</ul>":"\n</ol>"
}
txt=(txt=(txt=lines.join("\n")).replace(/<a href=""?https?:(\/\/[a-z.]*whirlpool\.net\.au)/gi,'<a href="$1').replace(/(<a.+?>)https?:\/\/[a-z.]*whirlpool\.net\.au(\/.+?<\/a>)/g,"$1$2").replace(/<a href=""?(\/\/[a-z.]*whirlpool\.net\.au)/gi,'<a class="internal" href="$1').replace(/<a href=""?(\/[^\/])/gi,'<a class="internal" href="$1').replace(/<a href=""?https?:\/\/whrl\.pl/gi,'<a class="internal" href="//whrl.pl').replace(/<a href=""?http/gi,'<a class="external" rel="nofollow" target="_blank" href="http').replace(/<a href=""?mailto/gi,'<a class="email" href="mailto').replace(/<span class="wcauth"> ?([0-9]{1,12})([^<]+?)<\/span>/gi,'<span class="wcauth"><a onclick="jumpToReplyId($1);return false;" title="$1">$2</a></span>').replace(/<p class="reference">([0-9]{1,12}) (.+?)<\/p>/gi,'<p class="reference"><a onclick="jumpToReplyId($1);return false;" title="$1">$2</a></p>')).replace(/[\u2580\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588\u2589\u258A\u258B\u258C\u258D\u258E\u258F\u2590\u2591\u2592\u2593\u2594\u2595\u2596\u2597\u2598\u2599\u259A\u259B\u259C\u259D\u259E\u259F]/g,"waah ")
;if(enabled.linelimit){var inputString=txt;var outputArray=[];var maxColumns=30
;var spaceMatchRegexp=/^((<[^>]+>|[^<\s]){0,30}\s+)+/
;var ptagMatchRegexp=/^<\/?[p|h1|h2|h3|ul|ol|li|hr|br]>/;var entityMatchRegexp=/^&\w+?;/
;var charCount=0;var howMuchToGrab=0;while(inputString.length){if(inputString.length<maxColumns){
outputArray.push(inputString);break}var spaceMatches=inputString.match(spaceMatchRegexp)
;var ptagMatches=inputString.match(ptagMatchRegexp);if(spaceMatches&&spaceMatches[0]){
howMuchToGrab=spaceMatches[0].length;charCount=0
}else if("<"==inputString.charAt(0)&&inputString.indexOf(">")>-1){
howMuchToGrab=inputString.indexOf(">");if(inputString.match(ptagMatchRegexp))charCount=0
}else if(inputString.match(entityMatchRegexp)){
howMuchToGrab=inputString.indexOf(";")>-1?1+inputString.indexOf(";"):inputString.length;charCount++
}else{howMuchToGrab=1;charCount++}outputArray.push(inputString.substring(0,howMuchToGrab))
;inputString=inputString.substring(howMuchToGrab);if(charCount>=maxColumns){
outputArray.push("<wbr>");charCount=2}}txt=outputArray.join("")}
return txt=txt.replace(/\u00AB/g,"&lt;").replace(/\u00BB/g,"&gt;")}

/*!
Whirlcode3 parser | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
(function(root){"use strict";var WC3_LT="";var WC3_GT="";var WC3_ENT_AMP="";var WC3_ENT_LT=""
;var WC3_ENT_GT="";var WC3_ENT_QUOT="";var WC3_ENT_APOS="";var WC3_ENT_PIPE=""
;var WC3_ENT_LBRACK="";var WC3_ENT_RBRACK="";var WC3_ENT_STAR="";var WC3_ENT_HASH=""
;var WC3_ENT_COLON="";var WC3_ENT_DOLLAR="";var WC3_ENT_EQ="";var WC3_ENT_AT="";var WC3_BR=""
;var WC3_FENCE="";var WC3_LT_RE=new RegExp(WC3_LT,"g");var WC3_GT_RE=new RegExp(WC3_GT,"g")
;var WC3_BR_RE=new RegExp(WC3_BR,"g");var WC3_FENCE_RE=new RegExp(WC3_FENCE,"g")
;var WC3_FENCE_LINE_RE=new RegExp("^"+WC3_FENCE+"(\\d+)"+WC3_FENCE+"$")
;var WC3_FENCE_DELIM_RE=/^\s*\$\$\s*$/;var WC3_PRE_LINE_RE=/^\$(\s|$)/
;var WC3_PLACEHOLDER_RE=/[\uE000-\uE00F]/g;var WC3_ENT_RE=/[\uE002-\uE00F]/g;var WC3_ENT_RESTORE={
"":"&amp;","":"&lt;","":"&gt;","":"&quot;","":"&apos;","":"|","":"[","":"]","":"*","":"#",
"":":","":"$","":"=","":"@"};var WC3_MAX_COLUMNS=30;function whirlcode3(src,params){
var opts=wc3_options(params);return wc3_render(wc3_parse(src,opts),opts)}
whirlcode3.parse=function(src,params){return wc3_parse(src,wc3_options(params))}
;function wc3_options(params){var enabled={whirlcode:true,headings:false,paragraph:true,
entities:true,basichtml:true,autolink:true,wordfilter:true,linelimit:true,metablocks:false,
smileys:false};var i;if("string"===typeof params)enabled={whirlcode:params.indexOf("w")>-1,
headings:params.indexOf("h")>-1,paragraph:params.indexOf("p")>-1,entities:params.indexOf("e")>-1,
basichtml:params.indexOf("b")>-1,autolink:params.indexOf("a")>-1,wordfilter:params.indexOf("f")>-1,
linelimit:params.indexOf("l")>-1,metablocks:params.indexOf("m")>-1,smileys:params.indexOf("s")>-1
};else if(params&&"object"===typeof params)for(i in params)if(Object.prototype.hasOwnProperty.call(params,i))enabled[i]=params[i]
;return enabled}function wc3_parse(src,opts){var text=String(null==src?"":src);var fences=[]
;text=(text=(text=(text=text.replace(WC3_BR_RE,"")).replace(WC3_FENCE_RE,"")).replace(WC3_PLACEHOLDER_RE,"")).replace(/\r\n?/g,"\n")
;if(opts.paragraph)text=wc3_extractFences(text,fences);text=wc3_decodeEntities(text)
;if(!opts.smileys)text=text.replace(/[\uD800-\uDFFF]/g,"")
;if(opts.whirlcode)text=wc3_fixIosQuotes(text)
;if(opts.metablocks)text=text.replace(/\{\{[\s\S]+?\}\}/g,"")
;text=(text=(text=text.replace(/\u00A0/g," ")).replace(/</g,WC3_LT).replace(/>/g,WC3_GT)).replace(/([^\n]+)\n(?=([^\n]+))/g,(function(m,prev,next){
return prev+(wc3_isBlockLineStart(next)||WC3_FENCE_LINE_RE.test(prev)?"\n\n":"\n")
})).replace(/([^\n])\n([^\n])/g,"$1"+WC3_BR+"$2").replace(/\n\n+/g,"\n")
;if(opts.whirlcode)text=wc3_applyWhirlcodeMarkers(text)
;if(opts.basichtml)text=wc3_applyBasicHtml(text);if(opts.entities)text=wc3_applyEntities(text)
;if(opts.wordfilter)text=wc3_applyWordfilter(text);if(opts.autolink)text=wc3_applyAutolink(text)
;var rawLines=(text=text.replace(WC3_BR_RE,"<br>")).split("\n");var seeds=[];var i
;for(i=0;i<rawLines.length;i++){var seed=wc3_classifyLine(rawLines[i],opts,i,fences)
;if(seed)seeds.push(seed)}return{type:"doc",children:wc3_groupSeeds(seeds)}}
function wc3_extractFences(text,fences){var lines=text.split("\n");var out=[];var i=0
;while(i<lines.length){if(!WC3_FENCE_DELIM_RE.test(lines[i])){out.push(lines[i]);i++;continue}
var body=[];i++;while(i<lines.length&&!WC3_FENCE_DELIM_RE.test(lines[i])){body.push(lines[i]);i++}
if(i<lines.length)i++;out.push(WC3_FENCE+fences.length+WC3_FENCE);fences.push(body.join("\n"))}
return out.join("\n")}function wc3_protectFenceBody(body,opts){var s=String(null==body?"":body)
;s=(s=s.replace(WC3_PLACEHOLDER_RE,"")).replace(WC3_FENCE_RE,"")
;if(!opts.smileys)s=s.replace(/[\uD800-\uDFFF]/g,"")
;return s.replace(/</g,WC3_LT).replace(/>/g,WC3_GT)}function wc3_fixIosQuotes(txt){
return txt.replace(/\[[\u2018\u2019]/g,"['").replace(/\[[\u201C\u201D]/g,'["').replace(/[\u2018\u2019]\]/g,"']").replace(/[\u201C\u201D]\]/g,'"]').replace(/=[\u201C\u201D]/g,'="').replace(/[\u201C\u201D]>/g,'">')
}var WC3_MARKERS=wc3_buildMarkers([{code:"\\*",tag:"strong"},{code:"/",tag:"em"},{code:"\\\\",
tag:"sub"},{code:"\\^",tag:"sup"},{code:"-",tag:"strike"},{code:"#",tag:"tt"},{code:"\\(",
tag:"small",code2:"\\)"},{code:'"',tag:"span",plus:' class="wcrep1"'},{code:"'",tag:"span",
plus:' class="wcrep2"'},{code:"`",tag:"span",plus:' class="wcgrey"'},{code:"~",tag:"span",
plus:' class="wcserif"'},{code:"_",tag:"span",plus:' class="wcspoil"'},{code:"\\?",tag:"a",
plus:' href="http://www.google.com.au/search?q=$1"'}]);function wc3_buildMarkers(list){var i
;for(i=0;i<list.length;i++){var w=list[i]
;w.re=new RegExp("\\["+w.code+"(.+?)"+(w.code2?w.code2:w.code)+"\\]","g")
;w.replace=w.plus&&w.plus.indexOf("$1")>-1?wc3_attrReplacer(w):"<"+w.tag+(w.plus?w.plus:"")+">$1</"+w.tag+">"
}return list}function wc3_applyWhirlcodeMarkers(txt){
txt=(txt=txt.replace(/\[\[(([-A-Z0-9_ ]+?)\|(.+?)|([-A-Z0-9_ ]+?))\]\]/gi,'<a class="wiki" href="/wiki/$2$4">$3$4</a>')).replace(/"\/wiki\/([-A-Z0-9_ ]*)"/gi,(function(m,name){
return'"/wiki/'+name.replace(/ /g,"_")+'"'}));var i
;for(i=0;i<WC3_MARKERS.length;i++)txt=txt.replace(WC3_MARKERS[i].re,WC3_MARKERS[i].replace)
;return txt.replace(/\[\.(.+?)\.\]/g,"[$1]")}function wc3_attrReplacer(w){return function(match,p1){
var safe=p1.replace(/"/g,WC3_ENT_QUOT);var attrs=w.plus.replace(/\$1/g,(function(){return safe}))
;return"<"+w.tag+attrs+">"+p1+"</"+w.tag+">"}}var WC3_ALLOWED_HTML=wc3_buildAllowedHtml([{tag:"a",
inner:' href="(/|https?://|mailto:)[^"]+?"'},{tag:"strong|b"},{tag:"em|i"},{tag:"small"},{tag:"tt"
},{tag:"sup"},{tag:"sub"},{tag:"strike"}]);function wc3_buildAllowedHtml(list){var i
;for(i=0;i<list.length;i++){var a=list[i];var name=a.tag.split("|")[0]
;a.re=new RegExp(WC3_LT+"("+a.tag+")("+(a.inner?a.inner:"()")+")"+WC3_GT+"(.+?)"+WC3_LT+"/\\1"+WC3_GT,"ig")
;a.replace="<"+name+"$2>$4</"+name+">"}return list}function wc3_applyBasicHtml(txt){var i
;for(i=0;i<WC3_ALLOWED_HTML.length;i++)txt=txt.replace(WC3_ALLOWED_HTML[i].re,WC3_ALLOWED_HTML[i].replace)
;return txt}function wc3_applyEntities(txt){return txt.replace(/ -- /g," — ").replace(/ - /g," – ")}
function wc3_applyWordfilter(txt){
return txt.replace(/\bfuck(ed|ing|er)/gi,"flapp$1").replace(/\bfuck/gi,"flap").replace(/\bcunt/gi,"legend")
}function wc3_applyAutolink(txt){
return txt=(txt=(txt=(txt=" "+txt).replace(/([^""\/=])\b(https?:\/\/)?whrl\.pl\/([A-Za-z0-9]+)(?![^<]*<\/a>)/gi,'$1<a href="https://whrl.pl/$3">whrl.pl/$3</a>')).replace(/([^""\/=])\b(https?:\/\/)([-A-Z0-9+&@#\/%?=~_|!:,.;\uE002]*(?:_\([-A-Z0-9+&@#\/%=~_|?!:,.;\uE002]*\)|[-A-Z0-9+&@#\/%=~_|\uE002]))(?![^<]*<\/a>)/gi,'$1<a href="$2$3">$2$3</a>')).replace(/([^"":=])\b(?:mailto:)?([A-Z0-9._%-]+@[A-Z0-9.-]+\.[A-Z]{2,4})\b(?![^<]*<\/a>)/gi,'$1<a href="mailto:$2">$2</a>')
}function wc3_classifyLine(line,opts,index,fences){
var l=line.replace(/^\s+/,"").replace(/(\s|<br>)+$/,"");if(!l.length)return null
;var fenceMatch=WC3_FENCE_LINE_RE.exec(l);if(fenceMatch){var fi=parseInt(fenceMatch[1],10);var body
;return{type:"pre",html:wc3_protectFenceBody(fences&&fi<fences.length?fences[fi]:"",opts),
fenced:true}}var seed=wc3_classifyContent(l,opts);if(!seed)return null
;var printable=wc3_seedPrintable(seed)
;if(index<2)printable=printable.replace(/<span class="wcspoil">.{1,4}<\/span>/g,"")
;if(0===(printable=printable.replace(/<[^>]*>?/g,"").replace(/&nbsp;/g,"").replace(/\s+/g,"").replace(/[,.:;''`]{1,3}/,"")).length)return null
;return seed}function wc3_seedPrintable(seed){if("hr"===seed.type)return"-----"
;if("quote_line"===seed.type||"rep_line"===seed.type)return wc3_seedPrintable(seed.child)
;if("table_row"===seed.type)return seed.cells.join(" ");if(null!=seed.html)return seed.html;return""
}function wc3_classifyContent(l,opts,state){state=state||{quote:0,rep:0,total:0}
;if(opts.headings&&/^===\s+/.test(l))return{type:"heading",level:2,html:l.replace(/^===\s+/,"")}
;if(opts.headings&&/^==\s+/.test(l))return{type:"heading",level:3,html:l.replace(/^==\s+/,"")}
;if(opts.headings&&/^=\s+/.test(l))return{type:"heading",level:4,html:l.replace(/^=\s+/,"")}
;if(opts.headings&&/^\+\+\{.+\}$/.test(l)){var inc=l.replace(/^\+\+\{(.+)\}$/,"$1");return{
type:"include",html:inc,title:inc}}if(opts.paragraph){var quoted=wc3_parsePrefix(l,":")
;if(quoted&&state.quote<1&&state.total<4){var inner=wc3_classifyContent(quoted.rest,opts,{
quote:state.quote+1,rep:state.rep,total:state.total+1});if(!inner)inner={type:"paragraph",html:""}
;return{type:"quote_line",child:inner}}}if(opts.paragraph){var rep=wc3_parsePrefix(l,WC3_GT)
;if(rep&&state.rep<3&&state.total<4){var repInner=wc3_classifyContent(rep.rest,opts,{
quote:state.quote,rep:state.rep+1,total:state.total+1});if(!repInner)repInner={type:"paragraph",
html:""};return{type:"rep_line",child:repInner}}}if(opts.paragraph&&WC3_PRE_LINE_RE.test(l))return{
type:"pre_line",html:l.substring(1).replace(/^\s+/,"")};if(opts.paragraph&&/^-----$/.test(l))return{
type:"hr"};if(opts.paragraph&&/^@[0-9]{1,12}\s+/.test(l)){
var ref=l.replace(/^@([0-9]{1,12})\s+/,"$1 ");return{type:"reference",
html:ref=ref.replace(/<br>/,"</p>\n<p>")}}if(opts.paragraph){
var list=wc3_parseListPrefix(l,4-state.total);if(list)return{type:"list_item",marker:list.marker,
html:list.html}}if(opts.paragraph&&/^\|/.test(l))return{type:"table_row",cells:wc3_splitTableRow(l)}
;return{type:"paragraph",html:l}}function wc3_isPlainMarkerStart(s){
if(WC3_FENCE_LINE_RE.test(s))return true;if("|"===s.charAt(0)||WC3_PRE_LINE_RE.test(s))return true
;if(/^-----$/.test(s))return true;if(/^@[0-9]{1,12}\s+/.test(s))return true
;if(/^={1,3}\s+/.test(s))return true;if(/^\+\+\{/.test(s))return true
;return null!=wc3_parseListPrefix(s)}function wc3_isBlockLineStart(line){
if(wc3_parsePrefix(line,":"))return true;if(wc3_parsePrefix(line,WC3_GT))return true
;return wc3_isPlainMarkerStart(line)}function wc3_parsePrefix(l,marker){
if(l.charAt(0)!==marker)return null;var rest=l.substring(1);var spaced=/^\s/.test(rest)
;if(!(rest=rest.replace(/^\s+/,"")).length)return{rest:""}
;if(!spaced&&!wc3_isNestableMarkerStart(rest))return null;return{rest:rest}}
function wc3_isNestableMarkerStart(s){if(":"===s.charAt(0))return true
;if(s.charAt(0)===WC3_GT)return true;return wc3_isPlainMarkerStart(s)}
function wc3_parseListPrefix(l,budget){if(null==budget)budget=4;if(budget<1)return null
;var marker="";var stars=0;var hashes=0;var i=0;while(i<l.length){var j=i
;if(marker.length)while(j<l.length&&/\s/.test(l.charAt(j)))j++;var ch=l.charAt(j)
;if("*"!==ch&&"#"!==ch)break;if(marker.length>=budget)break;if("*"===ch&&stars>=3)break
;if("#"===ch&&hashes>=3)break;marker+=ch;if("*"===ch)stars++;else hashes++;i=j+1}
if(!marker.length)return null;var rest=l.substring(i);if(/^\s/.test(rest))return{marker:marker,
html:rest.replace(/^\s+/,"")};if(rest.length&&("*"===rest.charAt(0)||"#"===rest.charAt(0)))return{
marker:marker,html:rest};return null}function wc3_splitTableRow(line){var s
;var parts=line.replace(/^\|/,"").replace(/\|$/,"").split("|");var cells=[];var i
;for(i=0;i<parts.length;i++)cells.push(parts[i].replace(/^\s+/,"").replace(/\s+$/,""));return cells}
function wc3_isTableSeparator(cells){if(!cells.length)return false;var i
;for(i=0;i<cells.length;i++)if(!/^:?-{3,}:?$/.test(cells[i]))return false;return true}
function wc3_tableAlignments(cells){var out=[];var i,cell,left,right;for(i=0;i<cells.length;i++){
left=":"===(cell=cells[i]).charAt(0);right=":"===cell.charAt(cell.length-1)
;if(left&&right)out.push("center");else if(right)out.push("right");else out.push("left")}return out}
function wc3_groupSeeds(seeds){var out=[];var i=0;while(i<seeds.length){var s=seeds[i]
;if("quote_line"===s.type){var qChildren=[];while(i<seeds.length&&"quote_line"===seeds[i].type){
qChildren.push(seeds[i].child);i++}out.push({type:"blockquote",children:wc3_groupSeeds(qChildren)})
;continue}if("rep_line"===s.type){var rChildren=[]
;while(i<seeds.length&&"rep_line"===seeds[i].type){rChildren.push(seeds[i].child);i++}out.push({
type:"rep_block",children:wc3_groupSeeds(rChildren)});continue}if("pre"===s.type){out.push(s);i++
;continue}if("pre_line"===s.type){var preParts=[];while(i<seeds.length&&"pre_line"===seeds[i].type){
preParts.push(seeds[i].html);i++}out.push({type:"pre",html:preParts.join("\n")});continue}
if("table_row"===s.type){var rows=[];while(i<seeds.length&&"table_row"===seeds[i].type){
rows.push(seeds[i]);i++}var header=rows.length>=2&&wc3_isTableSeparator(rows[1].cells)
;var align=null;if(header){align=wc3_tableAlignments(rows[1].cells);rows.splice(1,1)}out.push({
type:"table",header:header,align:align,rows:rows});continue}if("list_item"===s.type){var items=[]
;while(i<seeds.length&&"list_item"===seeds[i].type){items.push(seeds[i]);i++}out.push({
type:"list_group",items:items});continue}out.push(s);i++}return out}function wc3_render(ast,opts){
var html=wc3_renderNodes(ast.children||[],opts)
;html=(html=wc3_styleLinks(html)).replace(/[\u2580-\u259F]/g,"waah ")
;if(opts.linelimit)html=wc3_linelimit(html)
;return html=(html=(html=html.replace(/&/g,"&amp;")).replace(WC3_LT_RE,"&lt;").replace(WC3_GT_RE,"&gt;")).replace(WC3_ENT_RE,(function(m){
return WC3_ENT_RESTORE[m]}))}function wc3_renderNodes(nodes,opts){var parts=[];var i
;for(i=0;i<nodes.length;i++)parts.push(wc3_renderNode(nodes[i],opts));return parts.join("\n")}
function wc3_renderNode(node,opts){switch(node.type){case"paragraph":return"<p>"+node.html+"</p>"
;case"heading":return"<h"+node.level+">"+node.html+"</h"+node.level+">";case"hr":return"<hr>"
;case"pre":return"<pre>"+node.html+"</pre>";case"blockquote":
return"<blockquote>\n"+wc3_renderNodes(node.children,opts)+"\n</blockquote>";case"rep_block":
return'<blockquote class="wcrep1">\n'+wc3_renderNodes(node.children,opts)+"\n</blockquote>"
;case"reference":return'<p class="reference">'+node.html+"</p>";case"include":
return'<p class="include" title="'+node.title.replace(/"/g,WC3_ENT_QUOT)+'">(include: '+node.html+")</p>"
;case"list_group":return wc3_renderListGroup(node.items);case"table":return wc3_renderTable(node)
;default:return""}}function wc3_renderListGroup(items){var lines=[];var meta=[];var i,j
;for(i=0;i<items.length;i++){lines.push("<li>"+items[i].html+"</li>");meta.push(items[i].marker)}
for(i=0;i<lines.length;i++)for(j=3;j>=0;j--){var current=meta[i].charAt(j)
;if(current.length&&(0===i||current!==meta[i-1].charAt(j)))lines[i]=("*"===current?"<ul>\n":"<ol>\n")+lines[i]
;if(current.length&&(i===lines.length-1||current!==meta[i+1].charAt(j)))lines[i]+="*"===current?"\n</ul>":"\n</ol>"
}return lines.join("\n")}function wc3_renderTable(node){var parts=["<table>"]
;var r,c,row,tag,align,attr;for(r=0;r<node.rows.length;r++){row=node.rows[r]
;tag=node.header&&0===r?"th":"td";parts.push("<tr>");for(c=0;c<row.cells.length;c++){
attr="left"!==(align=node.align&&node.align[c]||"left")?' style="text-align:'+align+'"':""
;parts.push("<"+tag+attr+">"+row.cells[c]+"</"+tag+">")}parts.push("</tr>")}parts.push("</table>")
;return parts.join("")}function wc3_styleLinks(txt){
return txt.replace(/<a href=""?https?:(\/\/[a-z.]*whirlpool\.net\.au)/gi,'<a href="$1').replace(/(<a.+?>)https?:\/\/[a-z.]*whirlpool\.net\.au(\/.+?<\/a>)/g,"$1$2").replace(/<a href=""?(\/\/[a-z.]*whirlpool\.net\.au)/gi,'<a class="internal" href="$1').replace(/<a href=""?(\/[^\/])/gi,'<a class="internal" href="$1').replace(/<a href=""?https?:\/\/whrl\.pl/gi,'<a class="internal" href="//whrl.pl').replace(/<a href=""?http/gi,'<a class="external" rel="nofollow" target="_blank" href="http').replace(/<a href=""?mailto/gi,'<a class="email" href="mailto').replace(/<span class="wcauth"> ?([0-9]{1,12})([^<]+?)<\/span>/gi,'<span class="wcauth"><a onclick="jumpToReplyId($1);return false;" title="$1">$2</a></span>').replace(/<p class="reference">([0-9]{1,12}) (.+?)<\/p>/gi,'<p class="reference"><a onclick="jumpToReplyId($1);return false;" title="$1">$2</a></p>')
}function wc3_linelimit(txt){var outputArray=[];var maxColumns=WC3_MAX_COLUMNS
;var ptagMatchRegexp=/^<\/?(p|h[1-4]|ul|ol|li|hr|br)>/;var charCount=0;var howMuchToGrab=0;var pos=0
;var gt;while(pos<txt.length){if(txt.length-pos<maxColumns){outputArray.push(txt.substring(pos))
;break}var spaceLen=wc3_scanSpaceChunks(txt,pos);var entityLen;if(spaceLen){howMuchToGrab=spaceLen
;charCount=0}else if("<"===txt.charAt(pos)&&(gt=txt.indexOf(">",pos))>-1){howMuchToGrab=gt-pos
;if(ptagMatchRegexp.test(txt.slice(pos,pos+6)))charCount=0
}else if(entityLen=wc3_entityLength(txt,pos)){howMuchToGrab=entityLen;charCount++}else{
howMuchToGrab=1;charCount++}outputArray.push(txt.substring(pos,pos+howMuchToGrab))
;pos+=howMuchToGrab;if(charCount>=maxColumns){outputArray.push("<wbr>");charCount=2}}
return outputArray.join("")}function wc3_scanSpaceChunks(s,start){var pos=start;var end=start
;for(;;){var p=pos;var units=0;while(units<WC3_MAX_COLUMNS){var ch=s.charAt(p);if("<"===ch){
var close=s.indexOf(">",p+1);if(close<=p+1)break;p=close+1
}else if(""!==ch&&!/\s/.test(ch))p++;else break;units++}var q=p
;while(q<s.length&&/\s/.test(s.charAt(q)))q++;if(q===p)break;pos=q;end=q}return end-start}
function wc3_entityLength(s,pos){if("&"!==s.charAt(pos))return 0;var p=pos+1
;while(p<s.length&&/\w/.test(s.charAt(p)))p++;if(p===pos+1||";"!==s.charAt(p))return 0
;return p+1-pos}var WC3_NAMED_ENTITIES={amp:WC3_ENT_AMP,lt:WC3_ENT_LT,gt:WC3_ENT_GT,
quot:WC3_ENT_QUOT,apos:WC3_ENT_APOS,vert:WC3_ENT_PIPE,VerticalLine:WC3_ENT_PIPE,nbsp:" ",iexcl:"¡",
cent:"¢",pound:"£",curren:"¤",yen:"¥",brvbar:"¦",sect:"§",uml:"¨",copy:"©",ordf:"ª",laquo:"«",
not:"¬",shy:"­",reg:"®",macr:"¯",deg:"°",plusmn:"±",sup2:"²",sup3:"³",acute:"´",micro:"µ",para:"¶",
middot:"·",cedil:"¸",sup1:"¹",ordm:"º",raquo:"»",frac14:"¼",frac12:"½",frac34:"¾",iquest:"¿",
Agrave:"À",Aacute:"Á",Acirc:"Â",Atilde:"Ã",Auml:"Ä",Aring:"Å",AElig:"Æ",Ccedil:"Ç",Egrave:"È",
Eacute:"É",Ecirc:"Ê",Euml:"Ë",Igrave:"Ì",Iacute:"Í",Icirc:"Î",Iuml:"Ï",ETH:"Ð",Ntilde:"Ñ",
Ograve:"Ò",Oacute:"Ó",Ocirc:"Ô",Otilde:"Õ",Ouml:"Ö",times:"×",Oslash:"Ø",Ugrave:"Ù",Uacute:"Ú",
Ucirc:"Û",Uuml:"Ü",Yacute:"Ý",THORN:"Þ",szlig:"ß",agrave:"à",aacute:"á",acirc:"â",atilde:"ã",
auml:"ä",aring:"å",aelig:"æ",ccedil:"ç",egrave:"è",eacute:"é",ecirc:"ê",euml:"ë",igrave:"ì",
iacute:"í",icirc:"î",iuml:"ï",eth:"ð",ntilde:"ñ",ograve:"ò",oacute:"ó",ocirc:"ô",otilde:"õ",
ouml:"ö",divide:"÷",oslash:"ø",ugrave:"ù",uacute:"ú",ucirc:"û",uuml:"ü",yacute:"ý",thorn:"þ",
yuml:"ÿ",OElig:"Œ",oelig:"œ",Scaron:"Š",scaron:"š",Yuml:"Ÿ",circ:"ˆ",tilde:"˜",ensp:" ",emsp:" ",
thinsp:" ",zwnj:"‌",zwj:"‍",lrm:"‎",rlm:"‏",ndash:"–",mdash:"—",lsquo:"‘",rsquo:"’",sbquo:"‚",
ldquo:"“",rdquo:"”",bdquo:"„",dagger:"†",Dagger:"‡",bull:"•",hellip:"…",permil:"‰",prime:"′",
Prime:"″",lsaquo:"‹",rsaquo:"›",oline:"‾",frasl:"⁄",euro:"€",trade:"™",larr:"←",uarr:"↑",rarr:"→",
darr:"↓",harr:"↔",spades:"♠",clubs:"♣",hearts:"♥",diams:"♦"};var WC3_C1_TO_WIN1252={128:"€",130:"‚",
131:"ƒ",132:"„",133:"…",134:"†",135:"‡",136:"ˆ",137:"‰",138:"Š",139:"‹",140:"Œ",142:"Ž",145:"‘",
146:"’",147:"“",148:"”",149:"•",150:"–",151:"—",152:"˜",153:"™",154:"š",155:"›",156:"œ",158:"ž",
159:"Ÿ"};function wc3_codePointToString(code){if(!code||code>1114111)return null
;if(code>=55296&&code<=57343)return null;if(code>=57344&&code<=63743)return null
;if(code<32&&9!==code&&10!==code&&13!==code)return null;if(127===code)return null
;if(code>=128&&code<=159)return WC3_C1_TO_WIN1252[code]||null
;if(code<=65535)return String.fromCharCode(code);code-=65536
;return String.fromCharCode(55296+(code>>10),56320+(1023&code))}var WC3_SYNTAX_CODEPOINTS={
38:WC3_ENT_AMP,60:WC3_ENT_LT,62:WC3_ENT_GT,34:WC3_ENT_QUOT,39:WC3_ENT_APOS,124:WC3_ENT_PIPE,
91:WC3_ENT_LBRACK,93:WC3_ENT_RBRACK,42:WC3_ENT_STAR,35:WC3_ENT_HASH,58:WC3_ENT_COLON,
36:WC3_ENT_DOLLAR,61:WC3_ENT_EQ,64:WC3_ENT_AT};function wc3_decodeEntities(text){
var re=/&(#(?:x[0-9a-fA-F]+|[0-9]+)|[a-zA-Z][a-zA-Z0-9]+);/g
;return text.replace(re,(function(m,body){if("#"===body.charAt(0)){
var code="x"===body.charAt(1)||"X"===body.charAt(1)?parseInt(body.substring(2),16):parseInt(body.substring(1),10)
;if(Object.prototype.hasOwnProperty.call(WC3_SYNTAX_CODEPOINTS,code))return WC3_SYNTAX_CODEPOINTS[code]
;var ch=wc3_codePointToString(code);return null!=ch?ch:m}
if(Object.prototype.hasOwnProperty.call(WC3_NAMED_ENTITIES,body))return WC3_NAMED_ENTITIES[body]
;return m}))}root.whirlcode3=whirlcode3
;if("undefined"!==typeof module&&module.exports)module.exports=whirlcode3
})("undefined"!==typeof globalThis?globalThis:this);

/*! 
Navigation code | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
$((function(){var hamburgerTimer;var hamburgerIsOpen=false;var savedScrollTop=0
;function hamburgerOpen(){if(!hamburgerIsOpen){clearTimeout(hamburgerTimer)
;savedScrollTop=$(window).scrollTop()
;$("#root").css("width",$("#root").width()).css("min-height",$(window).height())
;$("body").addClass("noscroll").css("top",-savedScrollTop+"px");$("#hamburgerMask").addClass("show")
;$("#left").removeClass("sidebar").addClass("burgered");$("#hamburger").attr("aria-expanded","true")
;$("#left")[0].offsetLeft;$("#root, #left, #hamburgerMask").addClass("opened");hamburgerIsOpen=true}
}function hamburgerClose(){if(hamburgerIsOpen){
$("#root, #left, #hamburgerMask").removeClass("opened");$("#hamburgerMask").removeClass("show")
;$("#root").css("min-height","");$("body").removeClass("noscroll").css("top","")
;$(window).scrollTop(savedScrollTop);$("#hamburger").attr("aria-expanded","false")
;clearTimeout(hamburgerTimer);hamburgerTimer=setTimeout((function(){$("#root").css("width","")
;$("#left").removeClass("burgered").addClass("sidebar")}),600);hamburgerIsOpen=false}}
$("#hamburger").attr("aria-expanded","false").click((function(){
if(hamburgerIsOpen)hamburgerClose();else hamburgerOpen()}))
;$("#hamburgerMask").click(hamburgerClose);$(window).on("resize",hamburgerClose);var settingsTimer
;var settingsOpened=false;function settingsOpen(){$("#ub_settings, #ub_menu").addClass("opened")
;$("#ub_settings").attr("aria-expanded","true");settingsOpened=true}function settingsClose(){
$("#ub_settings, #ub_menu").removeClass("opened");$("#ub_settings").attr("aria-expanded","false")
;settingsOpened=false}$("#ub_settings").attr("aria-expanded","false").on("click",(function(b){
clearTimeout(settingsTimer);if(settingsOpened)settingsClose();else settingsOpen();return false}))
;$("#ub_menu").on("mouseleave",(function(b){clearTimeout(settingsTimer)
;settingsTimer=setTimeout(settingsClose,500)}))
;$("#ub_settings, #ub_menu").on("mouseenter",(function(b){clearTimeout(settingsTimer)}))
;var searchTimer;var searchFocusTimer;var searchOpened=false;function searchOpen(){
if(!searchOpened){$("#upperbar").addClass("up_search_expanded")
;$("#up_search_submit").attr("aria-expanded","true");clearTimeout(searchFocusTimer)
;searchFocusTimer=setTimeout((function(){$("#up_search_input").focus()}),700);searchOpened=true}}
function searchClose(){if(searchOpened){clearTimeout(searchFocusTimer)
;$("#upperbar").addClass("up_search_closing").removeClass("up_search_expanded")
;$("#up_search_submit").attr("aria-expanded","false");setTimeout((function(){
$("#upperbar").removeClass("up_search_closing")}),700);searchOpened=false}}
$("#up_search_submit").attr("aria-expanded","false").click((function(){
if($("#upperbar").hasClass("up_search_expanded"))return true;searchOpen();return false}))
;$("#up_search_form").on("focusout",(function(event){setTimeout((function(){
if(!event.delegateTarget.contains(document.activeElement))searchClose()}),100)}))
;$("html").on("touchstart",(function(e){clearTimeout(settingsTimer)
;settingsTimer=setTimeout(settingsClose,100);clearTimeout(searchTimer)
;searchTimer=setTimeout(searchClose,100)}))
;$("#ub_settings, #ub_menu, #up_search_form").on("touchstart",(function(e){e.stopPropagation()}))
;$(document).on("keydown",(function(e){if(27===e.which){hamburgerClose();settingsClose()
;searchClose()}}))}));

/*! 
Thread list paginator | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
function thread_page_list_html(id,pagecount){var edges=4;var collapse=pagecount>10;var html="("
;for(var i=1;i<=pagecount;i++){if(collapse&&i===edges+1){
html+='<a class="fold" href="javascript:void(0)" onclick="thread_page_list_all(\''+id+"',"+pagecount+');">&middot;&middot;&middot;</a>'
;i=pagecount-edges+1}html+='<a href="/thread/'+id+(1==i?"":"?p="+i)+'">'+i+"</a>"}return html+")"}
function thread_page_list_all(id,pagecount){var edges=4;var html="(";for(var i=1;i<=pagecount;i++){
html+='<a href="/thread/'+id+(1==i?"":"?p="+i)+'">'+i+"</a>"
;if(i>2*edges&&i<pagecount-1)html+="<wbr>"}html+=")"
;var el=document.querySelector("#tr"+id+" td span.pages");if(el)el.innerHTML=html}$((function(){
$("span.pages[data-thread]").each((function(){
this.innerHTML=thread_page_list_html(this.getAttribute("data-thread"),parseInt(this.getAttribute("data-pages"),10))
}))}));

/*! 
Replies pages | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
$((function(){if(document.querySelector("#replies #top_pagination")){
function generate_item(text,liclass,action,page){var li=document.createElement("li")
;var a=document.createElement("a");var txt=document.createTextNode(text)
;a.setAttribute("href",action);li.setAttribute("class",liclass)
;if((page||0)>=1)li.setAttribute("data-page",page);a.appendChild(txt);li.appendChild(a)
;li.appendChild(document.createElement("wbr"));return li}function generate_fragment(vars){
var read_to_page=0===read_to?0:Math.floor(read_to/20)+1;var nbsp=String.fromCharCode(160)
;var fragment=document.createDocumentFragment();var i=1
;if(1==vars.total&&vars.firstdate==vars.lastdate){
fragment.appendChild(generate_item(vars.lastdate,"last","#r1",true));return fragment}
fragment.appendChild(generate_item("first: "+vars.firstdate,"first",vars.baseurl,true))
;if(vars.total>1){var collapsed=false;var prevcollapsed=false;while(i<=vars.total){
prevcollapsed=collapsed
;if((collapsed=i>5&&i<=vars.total-5&&!(i>=vars.current-4&&i<=vars.current+4)&&vars.total>20)&&!prevcollapsed)fragment.appendChild(generate_item("▪▪▪","expand","#"))
;fragment.appendChild(generate_item(nbsp+i+nbsp,(i===vars.current?" current":"")+(vars.userarr.indexOf(i)>-1?" user":"")+(collapsed?" collapsed":"")+(i===read_to_page?" marker":""),vars.baseurl+(1===i?"":"?p="+i),i))
;i++}}fragment.appendChild(generate_item("last: "+vars.lastdate,"last",vars.baseurl+"?p=-1#bottom"))
;return fragment}function deploy_pagination(){
var pagination_top=document.getElementById("top_pagination")
;var pagination_foot=document.getElementById("foot_pagination")
;var pagination_fragment=generate_fragment(pages);if(pagination_top&&pagination_foot){
pagination_top.replaceChildren(pagination_fragment.cloneNode(true))
;pagination_foot.replaceChildren(pagination_fragment)
;var elems=document.querySelectorAll("ul.pagination li.expand a"),i
;for(i=0;i<elems.length;++i)elems[i].addEventListener("click",(function(e){e.preventDefault()
;pagination_top.classList.remove("collapsed");pagination_foot.classList.remove("collapsed")}))}}
if("undefined"===typeof window.pagination_done){window.pagination_done=true;deploy_pagination()}}}))
;function ajaxActionFailed(){alert("Sorry, that didn't work. Please try again.")}$((function(){
if(document.querySelector("#replies.watchable")){function watching_toggle(){$.ajax({
url:"/forum/ajax.cfm",type:"post",data:{action:is_watching?"stopWatching":"startWatching",
thread_id:thread_id},dataType:"json",success:function(result){
is_watching=1===result.is_watching?true:false;read_to=result.read_to;update_page_elements(true)},
error:ajaxActionFailed})}function mark_as_read(replynum){$.ajax({url:"/forum/ajax.cfm",type:"post",
data:{action:"markAtPoint",thread_id:thread_id,point:replynum},dataType:"json",
success:function(result){is_watching=1===result.is_watching?true:false;read_to=result.read_to
;update_page_elements(true)},error:ajaxActionFailed})}function mark_reply_as_read(){
var selectedreplynum;mark_as_read($(this).parents("div.replyblock").attr("data-replynum")-1)
;return false}function mark_page_as_read(){var lastreplynum
;mark_as_read($("#replylist div.replyblock:last").attr("data-replynum"));return false}
function update_page_elements(subsequent_change){
var lastreplynum=$("#replylist div.replyblock:last").attr("data-replynum")
;var read_to_page=0===read_to?0:Math.floor(read_to/20)+1
;$("#watch_button").removeClass("bactive").addClass(is_watching?"bactive":"").html(is_watching?"<span>Watching</span>":"<span>Watch</span>").show()
;if(!is_watching){$("#read_button").hide();$("#goto_watched").hide()}else if(read_to==lastreplynum){
$("#read_button").hide();$("#goto_watched").show()}else{
$("#read_button").html("<span>Mark page as read</span>").show();$("#goto_watched").hide()}
if(!is_watching)$("#watchbar").hide();else{var read_post=$("#replylist > div.num"+read_to)
;if(read_post.length)$("#watchbar").insertAfter(read_post).show();else $("#watchbar").hide()}
if(is_watching)$("#content").addClass("is_watching");else $("#content").removeClass("is_watching")
;if(subsequent_change){$("ul.pagination li.marker").removeClass("marker")
;$("ul.pagination li[data-page="+read_to_page+"]").addClass("marker")}}
$("#watch_button").off("click").on("click",watching_toggle)
;$("#read_button").off("click").on("click",mark_page_as_read)
;$("a.markasreadhere").off("click").on("click",mark_reply_as_read);update_page_elements(false)}}))
;$((function(){if(document.querySelector("#replies.private")){
$("#pm_unread_button").on("click",(function(){$.ajax({url:"/forum/ajax.cfm",type:"post",data:{
action:"unread",thread_id:thread_id},success:function(){window.location.href="/forum/1"},
error:ajaxActionFailed})}));$("#pm_close_button").on("click",(function(){
if(confirm("Are you sure you want to close this conversation? This can not be undone."))$.ajax({
url:"/forum/ajax.cfm",type:"post",data:{action:"close",thread_id:thread_id},success:function(){
location.reload()},error:ajaxActionFailed})}));$("#pm_delete_button").on("click",(function(){
if(confirm("Are you sure you want to delete this conversation? This can not be undone."))$.ajax({
url:"/forum/ajax.cfm",type:"post",data:{action:"delete",thread_id:thread_id},success:function(){
window.location.href="/forum/1"},error:ajaxActionFailed})}))
;$("#pm_totwam_button").on("click",(function(){
if(confirm("Move this conversation to TWAM? This can not be undone."))$.ajax({url:"/forum/ajax.cfm",
type:"post",data:{action:"totwam",thread_id:thread_id},success:function(){location.reload()},
error:ajaxActionFailed})}))}}));function prefersReducedMotion(){
return!!(window.matchMedia&&window.matchMedia("(prefers-reduced-motion: reduce)").matches)}
function isFreshNavigation(){try{
var entry=performance.getEntriesByType&&performance.getEntriesByType("navigation")[0]
;if(entry)return"navigate"===entry.type}catch(e){}
if(window.performance&&performance.navigation)return 0===performance.navigation.type;return true}
function replyBlockFromHash(hash){if(!hash)return $()
;if("bottom"===hash)return $("div.replyblock").last();var kind=hash.charAt(0)
;if("r"!==kind&&"s"!==kind)return $();var rid=hash.slice(1);if(!rid)return $()
;return $("#rr"+rid+", div.num"+rid).first()}function handleReplyHash(hash,opts){opts=opts||{}
;var elem=replyBlockFromHash(hash);if(1!==elem.length)return false;if("bottom"===hash){
jumpToElement(elem,false,false,false);return true}var kind=hash.charAt(0);if("r"===kind){
jumpToElement(elem,true,!!opts.smooth,true);return true}if("s"===kind){
jumpToElement(elem,true,!!opts.smooth,false);return true}return false}$((function(){
if(document.querySelector("#replies")){var hash=window.top.location.hash.slice(1)
;if(isFreshNavigation()&&hash){var ignoreScroll=true;var scrolledAway=false;handleReplyHash(hash,{
smooth:false});requestAnimationFrame((function(){ignoreScroll=false}))
;$(window).on("scroll.replyhash",(function(){if(!ignoreScroll)scrolledAway=true}))
;function reapplyAfterLayout(){$(window).off("scroll.replyhash")
;if(!scrolledAway&&window.top.location.hash.slice(1)===hash)handleReplyHash(hash,{smooth:false})}
if("complete"===document.readyState)$(window).off("scroll.replyhash");else $(window).one("load",reapplyAfterLayout)
}$(window).on("hashchange",(function(){handleReplyHash(window.top.location.hash.slice(1),{
smooth:!prefersReducedMotion()})}));$("#replies p.reference a").each((function(index,element){
if(element.title&&parseInt(element.title)>0){var tag=$(element);var id=parseInt(element.title)
;tag.attr("href","/forum/?action=replies&r="+id).attr("rel","nofollow").attr("title","")}}))}}))
;var replyHighlightTimer=null;function jumpToElement(elem,styled,smooth,animated){
if(void 0===styled)styled=true;if(void 0===smooth)smooth=true;if(void 0===animated)animated=true
;if(prefersReducedMotion()){smooth=false;animated=false}clearTimeout(replyHighlightTimer)
;$("#replylist div.highlighted").removeClass("highlighted animated");if(1===elem.length){
var oversized=elem.height()>=(window.innerHeight||document.documentElement.clientHeight)
;elem.removeClass("selected");try{elem[0].scrollIntoView({block:oversized?"start":"center",
behavior:smooth?"smooth":"auto"})}catch(error){elem[0].scrollIntoView()}if(styled){
elem.addClass("highlighted");if(animated){elem.addClass("animated")
;replyHighlightTimer=setTimeout((function(){
$("#replylist div.highlighted").removeClass("highlighted animated")}),9e3)}}}}
function jumpToReplyId(id){var elem=$("#rr"+id).first();if(1===elem.length){jumpToElement(elem)
;return false}document.location="/forum/?action=replies&r="+id;return false}function selectText(el){
var range=document.createRange();var sel=window.getSelection();sel.removeAllRanges();try{
range.selectNodeContents(el);sel.addRange(range)}catch(e){range.selectNode(el);sel.addRange(range)}}
var sad_attempt_cnt=0;var sad_attempt_user=0;function applyUserVoteClasses(userid,vote){
$("#replies div.user"+userid+" div.voteblock, #replies div.user"+userid+" div.voteblock-mobile").removeClass("open crusty unvoted voted1 voted2 voted4 voted5").addClass("voted"+vote)
}function submitUserVote(that,userid,vote){$(that).addClass("waiting")
;if(sad_attempt_user!==userid){sad_attempt_cnt=0;sad_attempt_user=userid}
if(1===vote&&sad_attempt_cnt<2){vote=2;sad_attempt_cnt+=1}$.ajax({url:"/forum/ajax.cfm",type:"post",
data:{action:"uservote",u:userid,v:vote},success:function(){$(that).removeClass("waiting")
;applyUserVoteClasses(userid,vote)},error:function(){$(that).removeClass("waiting")
;ajaxActionFailed()}})}function userVote(that,replyid,userid,vote){submitUserVote(that,userid,vote)}
function userVoteMobile(that,replyid,userid,vote){var $block=$(that).closest("div.voteblock-mobile")
;if(!$block.hasClass("open")||0===vote){
$("#replylist div.voteblock-mobile.open").removeClass("open");$block.addClass("open");return}
submitUserVote(that,userid,vote)}

/*! 
Reply forms | (c) Whirlpool.net.au | Copyrighted code. All rights reserved. */
$((function(){if(document.getElementById("replyformBlock")){
var enable_selection_quotes=document.querySelector("#replies.editmode")?false:true
;var reply_form_hideable=document.querySelector("#replies.justreply")?false:true
;var autosizeEnabled=true
;if(/iPhone|iPad|iPod|Android/i.test(navigator.userAgent))autosizeEnabled=false;var necroAcked=false
;var necroPendingAfterOpen=null;var necroCfg="object"===typeof necro&&necro?necro:null
;if(document.getElementById("quotifytool")){$("#quotifytool").on("focus",(function(){this.value=""
}));$("#quotifytool").on("paste",pasteExternal);function pasteExternal(e){
var clip=e.originalEvent&&e.originalEvent.clipboardData||e.clipboardData||window.clipboardData
;var pasted=clip?clip.getData("text")||clip.getData("Text")||"":"";if(pasted){e.preventDefault()
;applyQuotify(pasted);return}setTimeout((function(){
applyQuotify(document.getElementById("quotifytool").value)}),0)}function applyQuotify(raw){
var quotify=document.getElementById("quotifytool");var textarea=document.getElementById("body")
;var text=(raw||"").trim();quotify.value="";if(!text.length)return
;if(-1!==text.indexOf("Paste external quotes here"))return
;textarea.value=(textarea.value.trim()+"\n\n"+text.replace(/^(.+)$/gm,"['$1']")).trim()+"\n\n"
;setTimeout(focusOnReplyEnd,5);triggerAutosize();doPreview()}}
if(!reply_form_hideable)requestShowReplyForm();enableAutosize()
;$("#postReplyLink").on("click",replyPressed);function replyPressed(afterOpen){
var supportsTouch="ontouchstart"in window||navigator.maxTouchPoints>0
;requestShowReplyForm((function(){scrollToReplyForm();if(!supportsTouch)focusOnReplyEnd()
;if("function"===typeof afterOpen)afterOpen()}));return false}
$("button.quoteThisSelection").on("mousedown",(function(e){e.preventDefault()}))
;$("button.quoteThisSelection").on("click touchstart",quotePressed);function quotePressed(){
if(quoteData.ok)replyPressed(appendQuote);return false}$("#savebutton").on("click",savePressed)
;function savePressed(){saveDraft();return false}$("#clearbutton").on("click",clearPressed)
;function clearPressed(){if(confirm("Permanently delete this draft?")){clearDraft();clearReplyForm()
;triggerAutosize();doPreview();hideReplyForm()}return false}
$("#footer_reply_button").on("click",(function(){requestShowReplyForm();return false}))
;function necroActive(){if(!necroCfg||!necroCfg.mode||"ok"===necroCfg.mode)return false
;if(document.querySelector("#replyformBlock.hasdraft"))return false;if(necroAcked)return false
;return true}function requestShowReplyForm(afterOpen){if(!necroActive()){showReplyForm()
;if("function"===typeof afterOpen)afterOpen();return false}
necroPendingAfterOpen="function"===typeof afterOpen?afterOpen:null
;var dlg=document.getElementById("necro");if(dlg&&"function"===typeof dlg.showModal){if(!dlg.open){
var sx=window.scrollX,sy=window.scrollY;dlg.showModal();window.scrollTo(sx,sy)}}else{necroAcked=true
;showReplyForm();if(necroPendingAfterOpen)necroPendingAfterOpen();necroPendingAfterOpen=null}
return false}function necroReplyHere(){necroAcked=true;var dlg=document.getElementById("necro")
;if(dlg&&dlg.open)dlg.close();showReplyForm();var pending=necroPendingAfterOpen
;necroPendingAfterOpen=null;scrollToReplyForm();var supportsTouch
;if(!("ontouchstart"in window||navigator.maxTouchPoints>0))focusOnReplyEnd();if(pending)pending()}
function necroStartNewThread(){if(!necroCfg)return
;var body="Continuing discussion from "+necroCfg.threadPath;if(quoteData&&quoteData.ok){var q
;body=(body+"\n\n"+(citationString(quoteData.replyid,quoteData.uname)+quoteData.text).trim()).trim()
}var form=document.createElement("form");form.method="POST";form.action="/forum/ipost.cfm"
;function addField(name,value){var input=document.createElement("input");input.type="hidden"
;input.name=name;input.value=value;form.appendChild(input)}addField("action","newthread")
;addField("f",String(necroCfg.forumId));addField("head","");addField("body",body)
;addField("modewc","true");addField("modeht","true");addField("modewl","true")
;addField("modest","true");document.body.appendChild(form);form.submit()}
$("#necroReplyHere").on("click",(function(){necroReplyHere();return false}))
;$("#necroNewThread").on("click",(function(){necroStartNewThread();return false}))
;var necroDlg=document.getElementById("necro")
;if(necroDlg)necroDlg.addEventListener("cancel",(function(){necroPendingAfterOpen=null}))
;function showReplyForm(){$("#replyformBlock, #previewBlock").show()
;$("#footer_reply_button").hide();triggerAutosize();doPreview()
;if(is_watching&&thread_page==thread_pages)if(document.getElementById("modesu"))document.getElementById("modesu").checked=true
;return false}function hideReplyForm(){if(reply_form_hideable){
$("#replyformBlock, #previewBlock").hide();$("#footer_reply_button").show()}}
function scrollToReplyForm(){var formObj=document.getElementById("replyformBlock")
;if(!formObj)return
;var smooth=!(window.matchMedia&&window.matchMedia("(prefers-reduced-motion: reduce)").matches);try{
formObj.scrollIntoView({block:"center",behavior:smooth?"smooth":"auto"})}catch(error){
formObj.scrollIntoView()}}function focusOnReplyEnd(){zoomDisable();setTimeout(zoomEnable,500)
;var el=document.getElementById("body");moveCursorToEnd(el);el.focus()}function enableAutosize(){
if(autosizeEnabled)autosize(document.querySelectorAll("#body"))}function triggerAutosize(){
if(autosizeEnabled)autosize.update(document.querySelectorAll("#body"))}var viewportContent=null
;function zoomDisable(){var meta=document.querySelector("head meta[name=viewport]")
;if(meta&&null===viewportContent)viewportContent=meta.getAttribute("content")
;$("head meta[name=viewport]").remove()
;$("head").prepend('<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=0" />')
}function zoomEnable(){$("head meta[name=viewport]").remove()
;$("head").prepend('<meta name="viewport" content="'+(null!==viewportContent?viewportContent:"width=device-width, initial-scale=1.0, user-scalable=1")+'" />')
}function moveCursorToEnd(el){el.selectionStart=el.selectionEnd=el.value.length}
var draftsEnabled=document.querySelector("#replyformBlock.drafts")?true:false
;var existingDraft=document.querySelector("#replyformBlock.hasdraft")?true:false
;var draft_thread_id=thread_id;var draft_forum_id=0==thread_id?forum_id:0;var draft_id=0
;var lastSavedDraft={};var draftTouchedTimeout=null;var draftAutosaveTimeout=null
;var draftSaveInFlight=false;var draftFetchRetried=false;if(draftsEnabled){
$("#body, #head").off("input");$("#replyoptions input").off("change")
;$("#clearbutton, #savebutton").show();$("#body, #context, #head").on("input",draftTouched)
;$("input[name=context], #replyoptions input").on("change",draftTouched)
;$(window).on("pagehide",saveDraftUnload);$("#fm").on("submit",(function(e){resetDraftTimers()
;draftsEnabled=false;return true}));if(existingDraft)fetchDraft()}function draftLoad(d){
if(d.exists){var h=document.getElementById("head");if(h)h.value=d.head
;var g=document.getElementById("fgroup");if(g)g.value=d.fg
;document.getElementById("body").value=d.body;var c1=document.getElementById("context")
;var c2=document.querySelectorAll('input[type="radio"][name="context"]')
;if(null!=c1)c1.value=d.context;else if(c2.length>0)c2.forEach((function(i){
i.checked=i.value==d.context}));document.getElementById("modewc").checked=1===d.modewc
;document.getElementById("modeht").checked=1===d.modeht
;document.getElementById("modewl").checked=1===d.modewl
;document.getElementById("modest").checked=1===d.modest;draft_id=d.id;lastSavedDraft=draftFormData()
;showReplyForm();resetDraftTimers()}else{draft_id=0;clearReplyForm();hideReplyForm()
;resetDraftTimers()}}function draftTouched(){if(!draftsEnabled)return
;if(draftTouchedTimeout)clearTimeout(draftTouchedTimeout)
;draftTouchedTimeout=setTimeout(draftModified,800)}function draftModified(){
var dData=draftFormData();draftTouchedTimeout=null
;var dirty=JSON.stringify(dData)!==JSON.stringify(lastSavedDraft)
;var btn=document.getElementById("savebutton");btn.disabled=!dirty;if(dirty)btn.value="Save"
;if(draftAutosaveTimeout)clearTimeout(draftAutosaveTimeout)
;draftAutosaveTimeout=setTimeout(draftAutosave,7e3)}function draftAutosave(){
draftAutosaveTimeout=null;saveDraft()}function resetDraftTimers(){
if(draftTouchedTimeout)clearTimeout(draftTouchedTimeout)
;if(draftAutosaveTimeout)clearTimeout(draftAutosaveTimeout)
;document.getElementById("savebutton").disabled=true}function draftFormData(){
var h=document.getElementById("head");var g=document.getElementById("fgroup");var gg=0
;if(g)if(g.selectedIndex>=0)gg=g.options[g.selectedIndex].value
;var c=document.getElementById("context")
;var c2=document.querySelector('input[type="radio"][name="context"]:checked');return{action:"save",
draft_id:draft_id,thread_id:draft_thread_id,forum_id:draft_forum_id,fgroup_id:gg,head:h?h.value:"",
body:document.getElementById("body").value,context:c?c.value:c2?c2.value:"",
modewc:document.getElementById("modewc").checked?1:0,
modeht:document.getElementById("modeht").checked?1:0,
modewl:document.getElementById("modewl").checked?1:0,
modest:document.getElementById("modest").checked?1:0}}function fetchDraft(){$.ajax({
url:"/forum/ajax.cfm",type:"post",data:{action:"get",thread_id:draft_thread_id,
forum_id:draft_forum_id},dataType:"json",success:function(data){draftLoad(data);triggerAutosize()},
error:function(){if(!draftFetchRetried){draftFetchRetried=true;setTimeout(fetchDraft,3e3);return}
draftsEnabled=false}})}function saveDraft(){if(!draftsEnabled)return;var dData=draftFormData()
;if(!dData.body.trim().length){if(draft_id)clearDraft()
;document.getElementById("savebutton").disabled=true;return}
if(JSON.stringify(dData)===JSON.stringify(lastSavedDraft)){
document.getElementById("savebutton").disabled=true;return}if(draftSaveInFlight){
if(draftAutosaveTimeout)clearTimeout(draftAutosaveTimeout)
;draftAutosaveTimeout=setTimeout(draftAutosave,1e3);return}draftSaveInFlight=true;$.ajax({
url:"/forum/ajax.cfm",type:"post",data:dData,dataType:"json",success:function(data){
lastSavedDraft=dData;if(data&&isFinite(data.id)&&data.id>0){draft_id=data.id
;lastSavedDraft.draft_id=data.id}var btn=document.getElementById("savebutton");btn.disabled=true
;btn.value="Save"},error:function(){var btn=document.getElementById("savebutton");btn.disabled=false
;btn.value="Save failed — retry?"},complete:function(){draftSaveInFlight=false}})}
function saveDraftUnload(){if(!draftsEnabled)return;var dData=draftFormData()
;if(!dData.body.trim().length){if(!draft_id)return;var form_data
;(form_data=new FormData).append("action","clear");form_data.append("thread_id",draft_thread_id)
;form_data.append("forum_id",draft_forum_id);navigator.sendBeacon("/forum/ajax.cfm",form_data)
;return}if(JSON.stringify(dData)===JSON.stringify(lastSavedDraft))return;var form_data=new FormData
;for(var key in dData)form_data.append(key,dData[key])
;navigator.sendBeacon("/forum/ajax.cfm",form_data)}function clearDraft(){$.ajax({
url:"/forum/ajax.cfm",type:"post",data:{action:"clear",thread_id:draft_thread_id,
forum_id:draft_forum_id},success:function(){draft_id=0;lastSavedDraft={}}})}
function clearReplyForm(){var body=document.getElementById("body");body.value=""
;body.defaultValue="";var h=document.getElementById("head");if(h){h.value="";h.defaultValue=""}
var c=document.getElementById("context");if(c&&"text"===c.type){c.value="";c.defaultValue=""}
document.getElementById("fm").reset()}var quoteData={ok:false,replydiv:null,replyid:"",uname:"",
text:"",fail:""};function appendQuote(){if(quoteData.ok){var el=document.getElementById("body")
;var fullQuote=citationString(quoteData.replyid,quoteData.uname)+quoteData.text
;el.value=(el.value.trim()+"\n\n"+fullQuote).trim()+"\n\n";draftTouched();triggerAutosize()}}
function idOfLastQuotedPost(){var id="";var text=document.getElementById("body").value
;var re=/^@(\d+)/gm;var arr;while(null!==(arr=re.exec(text)))id=arr[1];return id}
function citationString(replyid,uname){if(replyid===idOfLastQuotedPost())return""
;return"@"+replyid+" "+uname+" writes...\n"}
var quoteExcludeSelector="blockquote, .wcrep1, p.reference, div.op, div.edited, wbr"
;function closestQuoteNode(node){while(node&&1!==node.nodeType)node=node.parentNode
;if(!node||"function"!==typeof node.closest)return null
;return node.closest("blockquote, .wcrep1, p.reference")}function selectionIsOnlyInsideQuote(range){
var startQuote=closestQuoteNode(range.startContainer)
;var endQuote=closestQuoteNode(range.endContainer);if(!startQuote||!endQuote)return false
;return startQuote===endQuote}function getQuote(){var delim=String.fromCharCode(31);var result={
ok:false,replydiv:null,replyid:"",uname:"",text:"",fail:""}
;if(!enable_selection_quotes)return result;var theSelection=null
;if(window.getSelection)theSelection=window.getSelection();else if(document.getSelection)theSelection=document.getSelection()
;if(null===theSelection||theSelection.isCollapsed||0===theSelection.rangeCount){
result.fail="No text was selected.";return result}var theRange=theSelection.getRangeAt(0)
;var node=theRange.commonAncestorContainer;while(node){
if(1===node.nodeType&&"div"==node.tagName.toLowerCase()){if(node.getAttribute("data-noquote"))break
;if(node.getAttribute("data-replyid")){result.replyid=node.getAttribute("data-replyid")
;result.uname=node.getAttribute("data-uname");result.replydiv=node;break}}
if(1===node.nodeType&&"body"==node.tagName.toLowerCase())break;if(node.nodeType>=4)break
;node=node.parentNode}if(""===result.replyid){result.fail="Selected text must be from one reply."
;return result}if(selectionIsOnlyInsideQuote(theRange)){
result.fail="Selected text is inside a quote.";return result}var fragment=theRange.cloneContents()
;var i,child,p;var qry=fragment.querySelectorAll(quoteExcludeSelector)
;for(i=0;i<qry.length;++i)qry[i].remove()
;var b=fragment.querySelectorAll("span, strong, em, b, i, tt, a, hr");for(i=0;i<b.length;++i){
p=(child=b[i]).parentNode;while(child.firstChild)p.insertBefore(child.firstChild,child)
;p.removeChild(child)}var a=fragment.querySelectorAll("p.tripleclick")
;for(i=0;i<a.length;++i)(p=(child=a[i]).parentNode).removeChild(child);var str=""
;var rt=fragment.querySelector("div.replytext");if(rt)str=rt.innerHTML;else{
var span=document.createElement("span");span.appendChild(fragment);str=span.innerHTML}var str2=""
;var strArr=(str=(str=(str=(str=str.replace(/(?:\r\n|\r|\n)/g," ")).replace(/<p(re)?>/g,delim).replace(/<\/p(re)?>/g," ").replace(/<[ou]l>/g,delim).replace(/<\/[ou]l>/g," ").replace(/<h[1-6]>/g,delim).replace(/<\/h[1-6]>/g," ").replace(/<li>/g,"").replace(/<\/li>/g,"\n").replace(/<br>/g,"\n")).replace(/\n\s+/g,"\n").replace(/\s+\n/g,"\n")).replace(/&amp;/g,"&")).split(delim)
;for(i=0;i<strArr.length;i++){var par=strArr[i].replace(/^\s+|\s+$/g,"")
;if(par.length)str2+='["'+par+'"]\n\n'}if(0===str2.length){
result.fail="No quotable text was selected.";return result}result.ok=true;result.text=str2
;return result}function updateQuoteButtons(){$("#replylist div.reply div.quote").hide()
;if(quoteData.ok)$(quoteData.replydiv).find("div.quote").show()}
function refreshQuoteFromSelection(){quoteData=getQuote();updateQuoteButtons()}
var selectionEndTimeout=null;document.addEventListener("selectionchange",(function(){
if(selectionEndTimeout)clearTimeout(selectionEndTimeout);selectionEndTimeout=setTimeout((function(){
selectionEndTimeout=null;refreshQuoteFromSelection()}),200)}))
;$("#fm").on("keydown","#body",(function(e){
if(("Enter"===e.key||13===e.keyCode)&&(e.metaKey||e.ctrlKey))$("#postbutton").trigger("click")}))
;$(document).on("mouseup",(function(e){
if($(e.target).closest("button.quoteThisSelection").length)return;if(selectionEndTimeout){
clearTimeout(selectionEndTimeout);selectionEndTimeout=null}refreshQuoteFromSelection()}))
;$("input[type=text], textarea").on("touchstart",(function(){zoomDisable()}))
;$("input[type=text], textarea").on("touchend",(function(){setTimeout(zoomEnable,500)}))
;$("#fm").on("submit",(function(e){
if(document.querySelectorAll('input[type="radio"][name="context"]').length>=1)if(1!=document.querySelectorAll('input[type="radio"][name="context"]:checked').length){
alert("You must select a thread to post into.");e.preventDefault();return false}return true}))
;var previewTimer;var previewWait=false;var previewInterval=600;var automaticPreview=true
;function execPreview(){var timerStart=new Date;var commentStr=document.getElementById("body").value
;if(commentStr.length>0)$("#livepreview").show()
;var settingStr="pfl"+(document.getElementById("modewc").checked?"w":"")+(document.getElementById("modeht").checked?"b":"")+(document.getElementById("modewl").checked?"a":"")+(document.getElementById("modest").checked?"e":"")
;var previewStr=whirlcode3(commentStr,settingStr)
;if(-1==[106,126,58,112,85].indexOf(forum_id))previewStr=previewStr.replace(/\b(premise)\b/gi,'<span class="disaster" onclick="disasterModal();">$1</span>')
;document.getElementById("previewSpan").innerHTML=previewStr;var timerEnd=new Date
;$("#serverpreview, #previewButton, #previewNote").hide()
;if(commentStr.length>1e4||timerEnd-timerStart>previewInterval){
if(automaticPreview)if(0==$("#update_preview").length){automaticPreview=false
;$("#previewTools").children("div").after('<span id="update_preview_note" style="vertical-align:top">'+"Your post is now taking a long time to automatically preview<br>"+'<input name="update_preview" id="update_preview" tabindex="57" style="width: 150px; font-family: Arial; font-size: 16px; " value="Update Preview" type="button">'+"</span>")
;$("#update_preview").on("click",execPreview)}}else if(!automaticPreview){automaticPreview=true
;$("#update_preview, #update_preview_note").remove()}}function doPreview(){
if(!automaticPreview)return;if($("#replyformBlock").is(":visible")){
if($("#previewBlock").is(":hidden"))$("#previewBlock").show();if(!previewWait){execPreview()
;previewWait=true;previewTimer=setTimeout((function(){execPreview();previewWait=false
}),previewInterval)}}}$("#body").on("input",doPreview)
;$("#modewc, #modeht, #modewl, #modest").on("change",doPreview)}}));

;(function($){$.fn.enableCheckboxRangeSelection=function(){var lastCheckbox=null;var $spec=this;$spec.off("click.checkboxrange").on("click.checkboxrange",function(e){if(lastCheckbox!=null&&(e.shiftKey||e.metaKey))$spec.slice(Math.min($spec.index(lastCheckbox),$spec.index(this)),Math.max($spec.index(lastCheckbox),$spec.index(this))+1).prop("checked",this.checked);else lastCheckbox=this});return this};$(function(){$("input.checkable").enableCheckboxRangeSelection()})})(jQuery);
