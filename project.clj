; # A programming story
;
; This is the story of a small program that parses source code. It converts
; comments into HTML (via [Markdown]) and nicely highlights code (using
; [SyntaxHighlighter]).
;
; [MarkDown]: https://github.com/sirthias/pegdown
; [SyntaxHighlighter]: http://alexgorbatchev.com/SyntaxHighlighter/

(defproject
  me.panzoo/story "0.0.1-SNAPSHOT"

  :description "A literate programming tool."

; The program is writtent in the [Clojure] language and uses the [pegdown]
; implementation of Markdown with a number of its extensions
; [enabled](#pegdown-extensions).
;
; [Clojure]: http://clojure.org
; [pegdown]: http://daringfireball.net/projects/markdown/

  :dependencies
  [[org.clojure/clojure "1.3.0"]
   [org.pegdown/pegdown "1.1.0"]
   ;[com.google.javascript/closure-compiler "r1592"]
   [org.clojure/tools.cli "0.2.1"]]
  
  :main me.panzoo.story)

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
; </pre></div>
;
; ### Command line usage
;
; `java -jar story.jar [-c|--comment <token>] [-o|-out <outfile>] <input>`
;
; For more detail run the program with the `--help` option or have a look at
; the [[commandline entry point]].

;%include src/me/panzoo/story.clj
