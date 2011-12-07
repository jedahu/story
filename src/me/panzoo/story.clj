 ; Copyright (c) 2011, Jeremy Hughes <jedahu@gmail.com>
 ;
 ; Permission to use, copy, modify, and/or distribute this software for any
 ; purpose with or without fee is hereby granted, provided that the above
 ; copyright notice and this permission notice appear in all copies.
 ;
 ; THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 ; WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 ; MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 ; ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 ; WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 ; ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 ; OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 ;
 ;
 ; cond-let, read-lines, and write-lines, are under the following license:
 ;
 ; Copyright (c) Rich Hickey. All rights reserved.  The use and
 ; distribution terms for this software are covered by the Eclipse Public
 ; License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
 ; be found in the file epl-v10.html at the root of this distribution.  By
 ; using this software in any fashion, you are agreeing to be bound by the
 ; terms of this license.  You must not remove this notice, or any other,
 ; from this software.

; # The code
;
; The code is Copyright (c) Jeremy Hughes 2011 and is available under the
; [ISC] license, except for the [[cond-let]],
; [[read-lines]], and [[write-lines]] functions which are Copyright (c) Rich
; Hickey and are under the [EPL]. These licenses are in the root of this source
; distribution in the files `LICENSE` and `epl-v10.html`.
;
; [ISC]: http://en.wikipedia.org/wiki/ISC_license
; [EPL]: http://www.eclipse.org/org/documents/epl-v10.php
;
; The usual stuff comes first. This code needs to read and write to files,
; manipulate strings, and parse Markdown.
(ns me.panzoo.story
  (:refer-clojure :exclude [comment])
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    fs)
  (:import
    [java.util.regex Pattern]
    [java.io BufferedReader BufferedWriter]
    [org.pegdown PegDownProcessor Extensions

; Internal links (to element ids) are nice to have. These imports will be used
; to create a modified LinkRenderer.
     LinkRenderer LinkRenderer$Rendering]
    [org.pegdown.ast WikiLinkNode])

; To run from the commandline this namespace must be compiled with a `-main`
; method. When run from the REPL or from Leiningen `gen-class` does nothing.
  (:gen-class))

; A few of the top level variables use these utility functions.

(declare normalize-anchor)
(declare html-escape)

; ## Top level variables
;
; A few objects need to be accessed by a number of functions: regular
; expressions that match the beginning of lines, SyntaxHighlighter options, a
; PegDownProcessor, and a modified LinkRenderer.


; ### Settings
;
; This dynamic variable is bound once in the commandline `-main` method and is
; never subsequently altered by the code in this namespace. The variable is
; bound to a map containing the following settings: which SyntaxHighlighter
; theme to use; which SyntaxHighlighter brushes to include to start with (other
; brushes may be added automatically by the program); what CSS file to include
; if any; and whether to log messages to std-err or not.
(def ^:dynamic *settings*
  {:theme "shThemeEclipse.css"
   :static-brushes []
   :stylesheet nil
   :verbose? false})


; ### Per input file
;
; These variables are rebound for every input file.

(def ^:dynamic *single-comment*
  "A string containing a single comment token."
  ";")

(def ^:dynamic *language*
  "A language string for use with SyntaxHighlighter."
  :clojure)

(def ^:dynamic *path*
  "The path of the file currently being processed."
  nil)


; ### Per output stream
;
; These variables are given bindings only in the [[render-files]] function.
;
; Brushes can be added by the program whenever a new file is processed.
(def ^:dynamic *brushes* nil)

(def ^:dynamic *code-anchors* nil)


; ### Language map
;
; Including SyntaxHighlighter brush files automatically based on file suffix or
; the language argument to an include directive is preferrable to explicitly
; listing brushes on the commandline or in a program that calls this one.
; Comment syntax is listed here too for the same reason.
;
; Don't use `languages` and `language-aliases` directly. Use [[lang-info]],
; [[lang-brush]], and [[lang-comment]] instead. Those three functions route
; their input through [[canonical-lang]] which resolves any aliases to their
; canonical equivalent.

(def languages
  "A map of language names to a pairs of comment syntax and SyntaxHighlighter
  brush file names."
  {:clojure [";" "shBrushClojure.js"]
   :applescript ["--" "shBrushAppleScript.js"]
   :as3 ["//" "shBrushAS3.js"]
   :bash ["#" "shBrushBash.js"]
   :shell ["#" "shBrushBash.js"]
   :sh ["#" "shBrushBash.js"]
   :cpp ["//" "shBrushCpp.js"]
   :c++ ["//" "shBrushCpp.js"]
   :cxx ["//" "shBrushCpp.js"]
   :c ["//" "shBrushCpp.js"]
   :c# ["//" "shBrushCSharp.js"]
   :c-sharp ["//" "shBrushCSharp.js"]
   :csharp ["//" "shBrushCSharp.js"]
   :delphi ["//" "shBrushDelphi.js"]
   :pascal ["//" "shBrushDelphi.js"]
   :erlang ["%" "shBrushErlang.js"]
   :erl ["%" "shBrushErlang.js"]
   :groovy ["//" "shBrushGroovy.js"]
   :java ["//" "shBrushJava.js"]
   :javafx ["//" "shBrushJavaFX.js"]
   :jfx ["//" "shBrushJavaFX.js"]
   :javascript ["//" "shBrushJScript.js"]
   :js ["//" "shBrushJScript.js"]
   :perl ["#" "shBrushPerl.js"]
   :pl ["#" "shBrushPerl.js"]
   :php ["#" "shBrushPhp.js"]
   :python ["#" "shBrushPython.js"]
   :py ["#" "shBrushPython.js"]
   :ruby ["#" "shBrushRuby.js"]
   :rb ["#" "shBrushRuby.js"]
   :sass ["//" "shBrushSass.js"]
   :scss ["//" "shBrushSass.js"]
   :scala ["//" "shBrushScala.js"]
   :sql ["--" "shBrushSql.js"]
   :vim ["\"" "shBrushVimscript.js"]
   :vimscript ["\"" "shBrushVimscript.js"]
   :vb ["'" "shBrushVb.js"]
   :vbnet ["'" "shBrushVb.js"]
   :markdown ["" nil]
   })

(def language-aliases
  "A map of aliases to canonical language names."
  {:clj :clojure
   :cljs :clojure
   :actionscript3 :as3
   :sh :bash
   :shell :bash
   :c :cpp
   :c++ :cpp
   :cxx :cpp
   :c# :csharp
   :c-sharp :csharp
   :erl :erlang
   :jfx :javafx
   :js :javascript
   :pl :perl
   :py :python
   :rb :ruby
   :sass :scss
   :vimscript :vim
   :vbnet :vb
   :md :markdown})


; ### Pegdown instances
;
; The same [pegdown processor][pp] and [link renderer][lr] are used for each
; program execution.
;
; [pp]: http://www.decodified.com/pegdown/api/org/pegdown/PegDownProcessor.html
; [lr]: http://www.decodified.com/pegdown/api/org/pegdown/LinkRenderer.html

(def processor
  "A PegDownProcessor set up with the following extensions:
  AUTOLINKS
  SMARTYPANTS
  FENCED_CODE_BLOCKS
  DEFINITIONS
  WIKILINKS"
  (PegDownProcessor. (bit-or
                       (. Extensions AUTOLINKS)
                       (. Extensions SMARTYPANTS)
                       (. Extensions FENCED_CODE_BLOCKS)
                       (. Extensions DEFINITIONS)
                       (. Extensions WIKILINKS))))

; Pegdown's link rendering is overriden for the wiki link case. Un-qualified
; links are given a qualified `href`, and qualified links are given an
; unqualified value. [[normalize-anchor]] is used to normalize the `href`. For
; example, in the file path/to/foo.c, these wiki links:
;
; ~~~~
; [[one link]]
; [[path/to/bar.c/another link]]
; ~~~~
;
; create this output:
;
; ~~~~
; <a href='#path/to/foo.c/one-link'>one link</a>
; <a href='#path/to/bar.c/another-link'>another link</a>
; ~~~~
(def link-renderer
  "A pegdown LinkRenderer that renders wiki links as links to internal
  document fragments rather than external HTML pages."
  (letfn [(def-name [s] (re-find #"(?<=/)[^/]+$" s))
          (anchor-name [s]
            (html-escape (or (def-name s) s)))]
    (proxy [LinkRenderer] []
      (render
        ([node]
         (if-let [text (and (instance? WikiLinkNode node)
                            (.getText node))]
           (try
             (LinkRenderer$Rendering.
                           (str
                             "#"
                             (normalize-anchor text))
                           (anchor-name text))
             (catch java.io.UnsupportedEncodingException _
               (throw (IllegalStateException.))))
           (proxy-super render node)))
        ([node text]
         (proxy-super render node text))
        ([node url title text]
         (proxy-super render node url title text))))))


; ## Utilities
;
; A few utility functions to make it easier to print messages to the console,
; iterate side-effects through a collection, access resources, encode a string
; for use as an id or fragment identifier, and bind an output stream to `*out*`
; over a lexical scope.

(defmacro cond-let
  "Takes a binding-form and a set of test/expr pairs. Evaluates each test one
  at a time. If a test returns logical true, cond-let evaluates and returns
  expr with binding-form bound to the value of test and doesn't evaluate any of
  the other tests or exprs. To provide a default value either provide a literal
  that evaluates to logical true and is binding-compatible with binding-form,
  or use :else as the test and don't refer to any parts of binding-form in the
  expr. (cond-let binding-form) returns nil."
  [bindings & clauses]
  (let [binding (first bindings)]
    (when-let [[test expr & more] clauses]
      (if (= test :else)
        expr
        `(if-let [~binding ~test]
           ~expr
           (cond-let ~bindings ~@more))))))

(defn message
  "Write s to standard error."
  [& s]
  (when (:verbose? *settings*)
    (binding [*out* (io/writer System/err)]
      (println (apply str s)))))

(defn each
  "Apply f to each item of coll in order for side effects only. Evaluates coll
  strictly, unlike for. Returns nil."
  [f coll]
  (doseq [c coll]
    (f c)))

(defn lazy-each
  ""
  [f coll]
  (lazy-seq
    (when (seq coll)
      (cons (f (first coll)) (lazy-each f (rest coll))))))

(defn slurp-resource
  "Get the complete contents of a Java resource. Throws an exception on
  failure unless continue-on-failure? is truthy."
  [resource-name & continue-on-failure?]
  (try
    (-> (.getContextClassLoader (Thread/currentThread))
      (.getResourceAsStream resource-name)
      (java.io.InputStreamReader.)
      (slurp))
    (catch Exception e
      (message "Failed to read resource " resource-name)
      (if continue-on-failure?
        (message "    continuing anyway...")
        (do (message "    aborting...")
          (throw e))))))

(defn slurp-file|resource
  "Get the contents of the file at path, or failing that, the contents of a
  Java resource. Throws an exception on failure unless continue-on-failure? is
  truthy."
  [path & continue-on-failure?]
  (try (slurp path)
    (catch java.io.FileNotFoundException _
      (message "Using internal " path)
      (slurp-resource path continue-on-failure?))))

(defn encode-anchor
  "Encode s for use as a fragment identifier."
  [s]
  (.replaceAll
    (if (some #{\/} s) s (str *path* "/" s))
    "\\s" "-"))

(defn normalize-anchor
  [s]
  "Encode s using encode-anchor and ensure it is qualified by file path."
  (encode-anchor (if (some #{\/} s) s (str *path* "/" s))))

(defn html-escape
  "Escape s for inclusion in HTML."
  [s]
  (string/escape
    s
    {\< "&lt;"
     \> "&gt;"
     \& "&amp;"}))


; ### IO helpers

(defmacro with-out-stream
  "Wrap an output stream using clojure.java.io/writer bind it to *out*, then
  execute body."
  [out & body]
  `(with-open [w# (io/writer ~out)]
     (binding [*out* w#]
       ~@body)))

(defn read-lines
  "Like clojure.core/line-seq but opens r with reader.  Automatically
  closes the reader AFTER YOU CONSUME THE ENTIRE SEQUENCE."
  [r]
  (let [read-line (fn this [^BufferedReader rdr]
                    (lazy-seq
                      (if-let [line (.readLine rdr)]
                        (cons line (this rdr))
                        (.close rdr))))]
    (read-line (io/reader r))))

(defn write-lines
  "Writes lines (a seq) to f, separated by newlines.  f is opened with
  writer, and automatically closed at the end of the sequence."
  [w lines]
  (with-open [^BufferedWriter writer (io/writer w)]
    (loop [lines lines]
      (when-let [line (first lines)]
        (.write writer (str line))
        (.newLine writer)
        (recur (rest lines))))))

(defn map-lines
  ""
  [r w f]
  (write-lines w (map f (read-lines r))))


; ### SyntaxHighlighter language helpers

(defn canonical-lang
  "Given a language keyword, return its canonical equivalent."
  [l]
  (let [k (keyword l)]
    (get language-aliases k k)))

(defn lang-info
  "Given a language keyword, return its information as a vector pair of
  comment syntax and SyntaxHighlighter brush name."
  [l]
  (languages (canonical-lang l)))

(defn lang-comment
  "Given a language keyword, return its comment syntax."
  [l]
  (first (lang-info l)))

(defn lang-brush
  "Given a language keyword, return its default SyntaxHighlighter brush name."
  [l]
  (second (lang-info l)))


; ### Regular expression helpers

(defn ?match [s reg]
  (when-let [m (re-find (reg) s)]
    (.substring s (count m))))

(defn comment
  "A regular expression matching the beginning of a commented line."
  []
  (re-pattern (str "^" (Pattern/quote *single-comment*))))

(defn markdown*
  ""
  []
  (if (= :markdown (canonical-lang *language*))
    (comment)
    (re-pattern (str (comment) " "))))

(defn markdown
  ""
  []
  (re-pattern (str (markdown*) "|" (comment) "$")))

(defn hidden-comment
  "A regular expression matching the beginning of a commented line that will
  not appear in the output."
  []
  (re-pattern (str "^\\s+" (Pattern/quote *single-comment*))))

(defn anchor
  "A regular expression matching the beginning of an anchor line."
  []
  (re-pattern (str (comment) "@ *")))

(defn heading
  "A regular expression matching the beginning of a heading line."
  []
  (re-pattern (str (markdown*) "\\#+ *")))

(defn include
  "A regular expression matching the beginning of an include line."
  []
  (re-pattern (str (comment) "%include +")))

(defn required
  "A regular expression matching the beginning of a require line."
  []
  (re-pattern (str (comment) "%require +")))

(defn test-begin
  "A regular expression matching a test-begin line."
  []
  (re-pattern (str (comment) "<\\?")))

(defn test-end
  "A regular expression matching a test-end line."
  []
  (re-pattern (str (comment) "\\?>")))


; ## Parsing
;
; Parsing is done by looping through a lazy list of lines. A character stream
; backed by that lazy list is used when reading across lines is necessary. See
; the `:else` case in [[classify-line]] and [[code-anchor-id :clojure]] for an
; example.
;
; The entry-point for parsing is the [[gather-lines]] function at the end of
; this section.

(defn lines-reader [lines]
  (let [lines-left (atom (interpose "\n" (filter seq lines)))]
    (java.io.PushbackReader.
      (proxy [java.io.Reader] []
        (close [])
        (read

; The clojure reader only calls the zero arity read method. Punting on
; implementing the other arities for now.
          ([]
           (let [ll @lines-left
                 line (first ll)
                 c (first line)]
             (reset! lines-left
                     (if (seq (rest line))
                       (cons (subs line 1) (rest ll))
                       (rest ll)))
             (if c (int c) -1))))))))

; Anchors for definitions are generated using the current line and a reader
; from the current line. Methods are expected to return a string identifying
; the definition, or `nil` if there is no definition beginning at the current
; line. The default method unconditionally returns `nil`.
(defmulti code-anchor-id
  "If the current line (or reader) begins a definition, return the definition
  name, otherwise return nil."
  (fn [line reader] *language*)
  :default :default)

(defmethod code-anchor-id :default [_ _] nil)

; The clojure method uses the clojure reader, which should be more robust
; than methods which use regular expressions.
(defmethod code-anchor-id :clojure [line reader]
  (when (re-find #"^\(" line)
    (let [code (read reader)]
      (cond
        (= 'defmethod (first code))
        (str (second code) " " (nth code 2))

        (re-find #"^def" (str (first code)))
        (name (second code))

        :else nil))))

; Not all of these methods are well tested, like this javascript one.
(defmethod code-anchor-id :javascript [line reader]
  (or (second (re-find #"^\s*function\s+([^\s\(]+)" line))
      (second (re-find #"^var\s+([A-Za-z_][A-Za-z_0-9]*)\s*=\s*function[\s\(]" line))
      (second (re-find #"^\s*'?([A-Za-z_][A-Za-z_0-9]*)'?\s*:\s*function[\s\(]" line))))

(defmethod code-anchor-id :vim [line reader]
  (second (re-find #"^function!?\s+([a-zA-Z_][a-zA-Z_0-9]*)" line)))

; The line parser emits a list of `[<classification> <data>]` pairs. This
; function calls [[code-anchor-id]] and returns an `[:anchor <id>]` pair.
; It also stores the anchor information for future use by [[code-anchor-toc]],
; which generates a table of contents for definitions with anchors.
(defn maybe-code-anchor
  "If the current line (or reader) begins a definition, store the definition
  name in *code-anchors* with the current file path as its key. Return the
  path qualified id, or return nil."
  [line reader]
  (if-let [id (code-anchor-id line reader)]
    (do
      (swap! *code-anchors*
             (fn [old]
               (update-in old [*path*] #(conj (or % []) id))))
      [[:anchor (str *path* "/" id)]])
    []))

(defn include-info
  ""
  [s]
  (let [[path & [token lang]] (string/split s #"\s+")
        lang (canonical-lang (or lang
                                 (re-find #"(?<=\.)[^/.]+$" path)
                                 *language*))
        comment (or token
                    (lang-comment (or lang *language*))
                    *single-comment*)]
    [path comment lang]))

; Each line of a source file will be either code, comment, anchor, include, or
; test code. Headings are treated as both anchors and comments.
(defn classify-line
  "Classify the first line in lines. Commented lines prefixed by white space
  are ignored. Returns a vector containing a single pair of the form
  [<classification> <data>], where <data> is a string except in the case of an
  include where it is a vector of [<path> & [<comment-syntax> <language>]]."
  [[line :as lines]]
  (let [m (partial ?match line)]
    (cond-let
      [text]
      (m include) [[:include (include-info text)]]
      (m required) []
      (m test-begin) [[:test-begin text]]
      (m test-end) [[:test-end text]]
      (m anchor) [[:anchor text]]
      (m heading) [[:anchor text]
                   [:comment (m markdown)]]
      (m markdown) [[:comment text]]
      (m hidden-comment) []
      :else (conj (maybe-code-anchor line (lines-reader lines))
                  [:code line]))))

; Each line is classified lazily.
(defn classify-lines
  "Classify each line in lines as :code, :comment, :anchor, or :include.
  Returns a lazy list of [<classification> <data>] pairs. See classify-line for
  more detail."
  [lines]
  (lazy-seq
    (when (seq lines)
      (concat (classify-line lines) (classify-lines (rest lines))))))

; Adjacent lines of the same classification need to be gathered together into
; a single string (except for anchors and includes).
(defn gather-lines-
  [lines]
  (lazy-seq
    (when-let [classfn (first (first lines))]
      (if (#{:comment :code} classfn)
        (let [[same tail] (split-with #(= classfn (first %)) lines)
              text (string/join "\n" (map second same))]
          (cons [classfn text] (gather-lines- tail)))
        (cons (first lines) (gather-lines- (rest lines)))))))

; The entry-point for parsing is this `gather-lines` function.
(defn gather-lines
  "Join adjacent strings of the same classification together (except :anchor
  and :include). Return a lazy list of [<classification> <data>] pairs. See
  classify-line for more detail."
  [lines]
  (gather-lines- (classify-lines lines)))


; ## Rendering
;
; The method that transforms each classified chunk into HTML, dispatches on the
; classification of each item in the list returned by [[gather-lines]].
(defmulti html<- first)

; SyntaxHighlighter brushes are associated with files if the language is
; supplied or can be worked out from the file suffix.
(defn maybe-associate-brush [path lang]
  (when-let [b (and lang (lang-brush lang))]
    (when-not (@*brushes* b)
      (message "Associating "path " with brush " b)
      (swap! *brushes* conj b))))

; The output of each file is wrapped in `article` or `section` tags. Article
; tags for top-level files and section tags for included ones.
(defmacro wrap-in-tags [tag & body]
  `(do
     (print ~tag)
     ~@body
     (print (str "</" (re-find #"(?<=^<)[^\s>]+" ~tag) ">"))))

(defn render-file [tag path & [token lang]]

; For each file, new bindings are made for comment syntax and language. If the
; language or comment syntax (`token`) are not supplied, they are guessed
; from the `path` suffix, and if that fails they are set to the values they
; have in the enclsing dynamic scope (i.e., from the commandline or the
; including file).
  (binding [*language* (canonical-lang
                         (or lang
                             (re-find #"(?<=\.)[^/.]+$" path)
                             *language*))]
    (binding [*single-comment* (or token
                                   (lang-comment *language*)
                                   *single-comment*)]
      (binding [*path* path]
        (wrap-in-tags tag
          (if (#{:markdown :md} *language*)

; A markdown file is a special case. It is treated as a single comment block.
            (each html<- (gather-lines (read-lines path)))
            (do
              (maybe-associate-brush path *language*)

; Lines of source code are read lazily by `line-seq` and gathered lazily by
; `gather-lines`, Along with printing each chunk of comment or code to an
; output stream, this ensures that the maximum memory used by this program will
; be determined by the largest comment or code chunk and not the total size of
; the source file.
              (each html<- (gather-lines (read-lines path))))))))))

; Code chunks are wrapped in a `pre` with the correct incantation for
; SyntaxHighlighter in its `class` attribute; comments are run through the
; Markdown processor; anchors become hrefless HTML anchors; and includes wrap
; the result of parsing and rendering the file they point to in `section` tags.

(defmethod html<- :code [[_ text]]
  (when-not (string/blank? text)
    (println (str "<pre class='brush: " (name *language*) "'>"
                  (html-escape text)
                  "</pre>"))))

(defmethod html<- :comment [[_ text]]
  (println (.markdownToHtml processor text link-renderer)))

(defmethod html<- :anchor [[_ text]]
  (println (str "<a id='" (normalize-anchor text) "'></a>")))

(defmethod html<- :include [[_ [path comment lang] :as args]]
  (render-file "<section>" path comment lang))

(defmethod html<- :test-begin [_]
  (println "<div class='test'>"))

(defmethod html<- :test-end [_]
  (println "</div>"))


; ## Look and feel
;
; A TOC is created for code anchors.
(defn code-anchor-toc []
  (wrap-in-tags "<div id='code-anchors'>"
    (wrap-in-tags "<ul>"
      (doseq [[path ids] (reverse @*code-anchors*)]
        (wrap-in-tags "<li>"
          (print (str path "<br>"))
          (wrap-in-tags "<ul>"
            (doseq [id (sort ids)]
              (wrap-in-tags "<li>"
                (wrap-in-tags (str "<a href='#"
                                   (encode-anchor (str path "/" id))
                                   "'>")
                  (print (html-escape id)))))))))))

; All javascript and CSS are inlined in the output.

(defn inline-js [s]
  (println "<script>" s "</script>"))

(defn inline-css [s]
  (println "<style>" s "</style>"))

; SyntaxHighlighter brushes can be set and the default theme
; (`shThemeEclipse.css`) overridden on the commandline. An additional
; stylesheet can also be set.
;
; Because lack of brush or theme will not materially affect the structure of
; the output page, failure to load one or more of them will not cause the
; program to halt, though it will be logged to standard-error.

(defn inline-brushes []
  (message "Adding the following brushes to output:")
  (each (partial message "    ") @*brushes*)
  (each inline-js
        (filter identity
                (map #(slurp-file|resource % :continue-on-failure)
                     @*brushes*))))

(defn inline-theme []
  (when-let [s (and (:theme *settings*)
                    (slurp-file|resource
                      (:theme *settings*)
                      :continue-on-faliure))]
    (inline-css s)))

(defn inline-stylesheet []
  (when-let [s (and (:stylesheet *settings*)
                    (slurp-file|resource
                      (:stylesheet *settings*)
                      :continue-on-failure))]
    (inline-css s)))

; The resulting look-and-feel resources are included together.
(defn look-and-feel []
  (inline-js (slurp-resource "XRegExp.js"))
  (inline-js (slurp-resource "shCore.js"))
  (inline-theme)
  (inline-js (slurp-resource "outliner.0.5.0.62.js"))
  (inline-css (slurp-resource "page.css"))
  (inline-stylesheet))

; Javascript configuration and entry-point for SyntaxHighlighter and TOC
; outline are included from an external resource.
(defn javascript-setup []
  (inline-js (slurp-resource "page.js")))


; ## Output
;
; The output is HTML5 which is why no `html`, `head`, or `body` tags are
; present. The order of things in the output file is fairly normal for HTML:
; style and behaviour information first, followed by visible content, followed
; by the javascript entry-point.
(defn render-files- [paths]
  (println
    "<!doctype html>"
    "<meta charset=utf-8>")
  (look-and-feel)

; Multiple input files are rendered to a single stream. The content of each
; file's rendering is wrapped in its own `article` tags.
  (each (partial render-file "<article>") paths)
  (code-anchor-toc)
  (inline-brushes)
  (javascript-setup))

(defn render-files [paths]

; [[*brushes*]] and [[*code-anchors*]] are rebound on every invocation of
; `render-files` to prevent unneeded brushes and anchors accumulating across
; calls.
  (binding [*brushes* (atom (or (set (:static-brushes *settings*)) #{})
                            :validator #(not (some (comp not string?) %)))
            *code-anchors* (atom (array-map))]
    (render-files- paths)))

; The program entry-point takes a list of paths to input files and a single
; output stream which can also be a file path or anything
; `clojure.java.io/writer` can handle.
(defn process-files
  "Take a list of file paths and an output stream, and render each file to the
  stream as HTML."
  [in-paths out]
  (with-out-stream out
    (render-files in-paths)))


; ## Testing
;
; Code that is wrapped in test tags `;<?` and `;?>`, where `;` is the comment
; syntax for the file, will be included in the output by default and wrapped
; in a `div` with the class `test`. To get a production tree without tests,
; run this program with the `--production` flag with a directory argument, or
; call [[write-production-tree]].
;
; To include files in the production tree but not in the documentation, use
; `;%require <file or directory>`, replacing `;` with the correct comment
; token.

(defn write-production-file
  "Write in-file to outdir with all test code commented (thus preserving line
  numbers)."
  [in-file outdir]
  (let [out-file (io/file outdir in-file)
        in-test? (atom false)]
    (.. out-file (getParentFile) (mkdirs))
    (map-lines
      in-file out-file
      (fn [line]
        (let [m (partial ?match line)]
          (cond-let
            [text]
            (m test-begin)
            (do (reset! in-test? true)
              line)

            (m test-end)
            (do (reset! in-test? false)
              line)

            :else
            (if @in-test?
              (str *single-comment* line)
              (cond-let
                [text]
                (m include)
                (let [[path comment lang] (include-info text)]
                  (binding [*path* path]
                    (binding [*single-comment* comment]
                      (binding [*language* lang]
                        (write-production-file path outdir))))
                  line)

                (m required)
                (do (if (fs/directory? text)
                      (fs/copy-tree text (io/file outdir (or (fs/dirname text) "")))
                      (fs/copy+ text (io/file outdir text)))
                  line)

                :else line))))))))

(defn write-production-tree
  "Write the contents of in-paths with recursive included and required files
  and directories to outdir, with any test code commented."
  [in-paths outdir]
  (when (fs/exists? outdir)
    (fs/deltree outdir))
  (fs/mkdir outdir)
  (doseq [p in-paths]
    (write-production-file p outdir)))


; ## Commandline
;
; Use this program from the commandline like so.
(def usage "Usage: java -jar story.jar [options] <input-files>")

(defn -main [& args]
  (let [[amap tail banner]
        (cli/cli args

; Here is a list of the options this program can take when run from the
; commandline.
                 ["-c" "--comment" "Comment syntax"]
                 ["-b" "--brush" "SyntaxHighlighter brush file" :multi true]
                 ["-t" "--theme" "SyntaxHighlighter theme file"]
                 ["-l" "--language" "SyntaxHighlighter language"]
                 ["-s" "--stylesheet" "A stylesheet file to include"]
                 ["-p" "--production" "Production directory."]
                 ["-v" "--verbose" "Turn on verbose output"
                  :default false :flag true]
                 ["-h" "--help" "Show this help" :default false :flag true])]

; For the brush, theme, and stylesheet options the program first tries to read
; from the filesystem and if that fails because the file is not found then
; an attempt is made to read from the program's resources. The resources
; contain the standard SyntaxHighlighter brushes and themes under their normal
; file names including an additional `shBrushClojure.js` and
; `shThemeClojure.css`.
;
; Multiple brushes can be used by repeating the `-b` or `--brush` options.
; Additional brushes may be added by the program as a result of file includes
; and file suffixes.
;
; If present, the `-p` or `--production` option prevents documentation
; generation, and instead writes a production tree to the supplied directory
; using [[write-production-tree]].

    (if (or (not (seq tail)) (:help amap))
      (do (println (str usage "\n" banner))
        (System/exit 1))
      (binding [*settings* {:theme (or (:theme amap) (:theme *settings*))
                            :stylesheet (:stylesheet amap)
                            :static-brushes (:brush amap)
                            :verbose? (:verbose amap)}]
        (binding [*single-comment* (or (:comment amap) *single-comment*)]
          (binding [*language* (canonical-lang (or (:language amap) *language*))]

; When no output file is given, the program renders to standard-out.
            (if-let [dir (:production amap)]
              (write-production-tree tail dir)
              (if (= 1 (count tail))
                (process-files tail *out*)
                (process-files (butlast tail) (last tail))))))))))
