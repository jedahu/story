;; # A programming story
;;
;; This is the story of a small program that parses source code. It converts
;; comments into HTML (via [Markdown]) and nicely highlights code (using
;; [SyntaxHighlighter]).
;;
;; [MarkDown]: http://daringfireball.net/projects/markdown/
;; [SyntaxHighlighter]: http://alexgorbatchev.com/SyntaxHighlighter/
(defproject
  me.panzoo/story "0.0.3-SNAPSHOT"

  :description "A literate programming tool."

  ;; The program is written in the [Clojure] language and uses the [pegdown]
  ;; implementation of Markdown with a number of its extensions
  ;; [enabled](#src/me/panzoo/story.clj/Pegdown-instances).
  ;;
  ;; [Clojure]: http://clojure.org
  ;; [pegdown]: https://github.com/sirthias/pegdown
  :dependencies
  [[org.clojure/clojure "1.3.0"]
   [org.pegdown/pegdown "1.1.0"]
   [fs "0.11.0"]

   ;; A [modified][m] `org.clojure/tools.cli` is used (for repeated options).
   ;; To be merged with upstream by v0.1.0.
   ;;
   ;; [m]: https://github.com/jedahu/tools.cli
   [me.panzoo/tools.cli "0.2.2"]]
  
  :main me.panzoo.story)

;; Check out the sources at https://github.com/jedahu/story
;;
;;
;; ## Synopsis
;;
;; To use this program you need to know four things: the single-line comment
;; syntax of your source-code, Markdown, the story syntax for anchors, and the
;; story syntax for includes.
;;
;; Comment blocks that are flush with the left margin are parsed and rendered
;; using Markdown, those prefixed with whitespace are ignored. Anchors are
;; comment lines whose content is '`@<anchor-id>`'. To include another source
;; file add a comment line whose content is '`%include <file-path>`'.
;;
;; By default this program is set up to process a Lisp like language with single
;; semi-colon comment tokens. A small source example with anchors, includes,
;; and wiki links might look like this:
;;
;; <pre class='brush: clojure'>
;; ;;<. Copyright (c) 2012, Me <me@example.com>
;; ;;
;; ;; Boring license notice.
;; ;; Blah blah blah.
;; ;;
;; ;; This section will not show in the documentation.
;; ;;.>
;;
;; ;; # My awesome program
;; ;;
;; ;; Introductory paragraph, uses *Markdown*. Fast forward to the
;; ;; [[last paragraph]].
;; ;;
;; ;; Blah blah blah.
;;
;; ;;. This comment will not show in the documentation.
;; (defn hello []
;;   (println "Hello world!"))
;;
;; ;;&lt;?
;; (assert (= (hello) "Hello world!"))
;; ;;?&gt;
;;
;; ;;%require config.xml
;; 
;; ;;%include fibonacci.clj
;; ;;%include fibonacci.js // javascript
;; ;;
;; ;;@last paragraph
;; ;; This is the last paragraph. Go back to the [[hello]] function. Go to the
;; ;; [[fibonacci.clj/fib]] function.
;; </pre>
;;
;; ### Includes
;;
;; As you can see, files written in different languages can be included. In this
;; example `fibonacci.js` is followed by the comment syntax and language name to
;; use with SyntaxHighlighter. For a number of languages (including javascript)
;; this information can be obtained from the file extension and the appropriate
;; brush file pulled in automatically; those languages are listed in the
;; [[src/me/panzoo/story.clj/Language map]].
;;
;; ### Test code and production
;;
;; Test code can be included inline using `<?` and `?>`, and commented out for
;; production. The `%require` directive marks a file or directory for production
;; use without including it in the documentation. See
;; [[src/me/panzoo/story.clj/Testing]] for more detail.
;;
;; ### Internal links
;;
;; Wiki style links (`[[link]]`) point to explicit anchors (`;@<id>`) or to
;; implicit anchors (the names of definitions). Implicit anchors are created
;; for Markdown headings (#-style only) and definitions in code (only for
;; languages with methods for
;; `me.panzoo.story/`[[src/me/panzoo/story.clj/code-anchor-id]]; be aware, not
;; all methods have been well tested).
;;
;; Wiki links to anchors in other files must be qualified by the file's path.
;; The markup for the link to `code-anchor-id` looks like this:
;; `[[src/me/panzoo/story.clj/code-anchor-id]]`.
;;
;; If any anchors for code definitions are created, an alphabetical TOC like
;; list of links to those anchors will appear on the right side of the page.
;;
;; ### Build instructions
;;
;; To build `story.jar` make sure [Leiningen] is installed and run the following
;; commands in the story project directory:
;;
;; ~~~~
;; lein deps
;; lein uberjar
;; ~~~~
;;
;; If all goes well a file named something like `story-x.x.x-standalone.jar`
;; will have magically appeared in the project directory. Rename it to
;; `story.jar` if you like.
;;
;; [Leiningen]: https://github.com/technomancy/leiningen
;;
;;
;; ### Command line usage
;;
;; ~~~~
;; java -jar story.jar input-files output
;; ~~~~
;;
;; If `output` is absent, the program's output is streamed to standard-out. For
;; more detail run the program with the `--help` option or have a look at the
;; [[src/me/panzoo/story.clj/Commandline]] section.
;;
;; This HTML document (assuming you are not reading the source file) was created
;; by the command: `java -jar story.jar project.clj index.html`.
;;
;;
;; ### Programmatic usage
;;
;; Call `me.panzoo.story/`[[src/me/panzoo/story.clj/process-files]] with a list
;; of file paths and an output stream or file-path. It may be necessary to set
;; up bindings for one or more of the dynamic variables described in the
;; [[src/me/panzoo/story.clj/Top level variables]] section depending on whether
;; the file suffixes are in the [[src/me/panzoo/story.clj/Language map]] or not.
;;
;; The equivalent of the above commandline invocation is: `(process-files
;; ["project.clj"] "index.html")`.
;;
;;%include src/me/panzoo/story.clj
;;%include resources/page.js
;;%include folding.vim
;;%require resources
