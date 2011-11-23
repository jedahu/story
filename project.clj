; # A programming story
;
; This is the story of a small program that parses source code. It converts
; comments into HTML (via [Markdown]) and nicely highlights code (using
; [SyntaxHighlighter]).
;
; [MarkDown]: http://daringfireball.net/projects/markdown/
; [SyntaxHighlighter]: http://alexgorbatchev.com/SyntaxHighlighter/

(defproject
  me.panzoo/story "0.0.2-SNAPSHOT"

  :description "A literate programming tool."

; The program is written in the [Clojure] language and uses the [pegdown]
; implementation of Markdown with a number of its extensions
; [enabled](#pegdown-extensions).
;
; [Clojure]: http://clojure.org
; [pegdown]: https://github.com/sirthias/pegdown

  :dependencies
  [[org.clojure/clojure "1.3.0"]
   [org.pegdown/pegdown "1.1.0"]
   ;[com.google.javascript/closure-compiler "r1592"]

; A [modified][m] `org.clojure/tools.cli` is used (for repeated options). A
; pull request is [pending][p] for upstream inclusion.
;
; [m]: https://github.com/jedahu/tools.cli
; [p]: https://github.com/clojure/tools.cli/pull/5

   [me.panzoo/tools.cli "0.2.2"]]
  
  :main me.panzoo.story)

; Check out the sources at https://github.com/jedahu/story

; ## Synopsis
;
; To use this program you need to know four things: the single-line comment
; syntax of your source-code, Markdown, the story syntax for anchors, and the
; story syntax for includes.
;
; Comment blocks that are flush with the left margin are parsed and rendered
; using Markdown. Anchors are comment lines whose content is '`@<anchor-id>`'.
; To include another source file add a comment line whose content is
; '`%include <file-path>`'.
;
; By default this program is set up to process a Lisp like language with single
; semi-colon comment tokens. A small source example with anchors, includes,
; and wiki links might look like this:
;
; <div class=code><pre class='brush: clojure'>
; ; # My awesome program
; ;
; ; Introductory paragraph. Fast forward to the [[code]].
; ;
; ; Blah blah blah.
;
; ;@code
; (defn hello []
;   (println "Hello world!"))
; 
; ; See the code [again!](#code)
;
; ;%include fibonacci.clj
; ;%include fibonacci.js // javascript
; </pre></div>
;
; As you can see, files written in different languages can be included. In this
; example `fibonacci.js` is followed by the comment syntax and language name to
; use with SyntaxHighlighter. For a number of languages this information can be
; obtained from the file extension and the appropriate brush file pulled in
; automatically; those languages are listed in the [[language map]].
;
; ### Build instructions
;
; To build `story.jar` make sure [Leiningen] is installed and run the following
; commands in the story project directory:
;
; ~~~~
; lein deps
; lein uberjar
; ~~~~
;
; If all goes well a file named something like `story-x.x.x-standalone.jar`
; will have magically appeared in the project directory. Rename it to
; `story.jar` if you like.
;
; [Leiningen]: https://github.com/technomancy/leiningen
;
; ### Command line usage
;
; ~~~~
; java -jar story.jar input-files > output
; ~~~~
;
; For more detail run the program with the `--help` option or have a look at
; the [[commandline]] section.
;
; This HTML document (assuming you are not reading the source file) was created
; by the command: `java -jar story.jar project.clj > index.html`.
;
;%include src/me/panzoo/story.clj
