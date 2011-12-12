// # Javascript setup
//
// This setup code is placed in a function purely to test/show-off story's
// anchor generation.
function me_panzoo_story_setup() {

// SyntaxHighlighter's defaults are not suitable, so they are tweaked.
  SyntaxHighlighter.defaults['toolbar'] = false;
  SyntaxHighlighter.defaults['smart-tabs'] = false;
  SyntaxHighlighter.defaults['gutter'] = false;
  SyntaxHighlighter.defaults['unindent'] = false;
  SyntaxHighlighter.defaults['class-name'] = 'code';
  SyntaxHighlighter.all();

// [h50](http://code.google.com/p/h5o/), the HTML5 outliner is used to create
// a table of contents.
  var outline = document.createElement('div');
  outline.setAttribute('id', 'outline');
  outline.innerHTML = HTML5Outline(document.body).asHTML(true);
  var children = outline.firstElementChild.firstElementChild.children;
  outline.removeChild(outline.firstElementChild);
  for (var i = 1; i < children.length; ++i) {
    outline.appendChild(children[i]);
  }
  document.body.appendChild(outline);

  var h1s = document.getElementsByTagName('h1');
  if (h1s.length > 0) document.title = h1s[0].innerText;

// Test code is hidden by default.
  var makeToggle = function(test) {
    var x = document.createElement('div');
    x.setAttribute('class', 'test-toggle');
    x.style.display = 'block';
    x.innerText = '?';
    x.onclick = function() {
      if (test.style.display == 'none') {
        test.style.display = 'block';
      } else {
        test.style.display = 'none';
      }
    };
    return x;
  };
  var tests = document.querySelectorAll('div.test');
  for (var i = 0; i < tests.length; ++i) {
    console.log('test', i, tests[i]);
    var test = tests[i];
    var tog = makeToggle(test);
    test.style.display = 'none';
    test.parentNode.insertBefore(tog, test);
  }

// A master toggle is created if there is more than one test section.
  if (tests.length > 1) {
    var masterTog = document.createElement('div');
    masterTog.setAttribute('id', 'master-toggle');
    masterTog.innerText = '? show all';
    masterTog.onclick = function() {
      var show = masterTog.innerText == '? show all';
      var display = show ? 'block' : 'none';
      masterTog.innerText = show ? '? hide all' : '? show all';
      for (var i = 0; i < tests.length; ++i) {
        tests[i].style.display = display;
      }
    };
    var e = document.querySelector('h1').nextSibling;
    e.parentNode.insertBefore(masterTog, e);
  }
}

me_panzoo_story_setup();
