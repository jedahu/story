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
}

me_panzoo_story_setup();
