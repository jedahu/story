; # The code
;
; The usual stuff comes first. This code needs to read and write to files,
; manipulate strings, and parse Markdown.
(ns me.panzoo.story
  (:refer-clojure :exclude [comment])
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli])
  (:import
    [java.util.regex Pattern]
    [org.pegdown PegDownProcessor Extensions

; Internal links (to element ids) are nice to have. These imports will be used
; to create a modified LinkRenderer.
     LinkRenderer LinkRenderer$Rendering]
    [org.pegdown.ast WikiLinkNode])

; To run from the commandline this namespace must be compiled with a `-main`
; method. When run from the REPL or from Leiningen `gen-class` does nothing.
  (:gen-class))

(declare encode-anchor)

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


; ### Per file
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

; Brushes can be added by the program whenever a new file is processed. They
; must be path strings. This variable is given a binding only in the
; `[[render-files]]` function.
(def ^:dynamic *brushes* nil)


; ### Language map
;
; Including SyntaxHighlighter brush files automatically based on file suffix or
; the language argument to an include directive is preferrable to explicitly
; listing brushes on the commandline or in a program that calls this one.
; Comment syntax is listed here too for the same reason.
;
; Some languages are commented out because they either do not have comments or
; because they only have block comments. Their dull lifeless forms remind the
; maintainer of this program to do something about that.
(def languages
  "A map of language names to a pairs of comment syntax and SyntaxHighlighter
  brush file names."
  {:clojure [";" "shBrushClojure.js"]
   :clj [";" "shBrushClojure.js"]
   :cljs [";" "shBrushClojure.js"]
   :applescript ["--" "shBrushAppleScript.js"]
   :actionscript3 ["//" "shBrushAS3.js"]
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
   })

(def language-aliases
  "A map of aliases to canonical language names."
  {:clj :clojure
   :cljs :clojure
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
   :vbnet :vb})


; ### Pegdown instances
;
; The same [pegdown processor][pp] and [link renderer][lr] are used for each
; program execution.
;
; [pp]: http://www.decodified.com/pegdown/api/org/pegdown/PegDownProcessor.html
; [lr]: http://www.decodified.com/pegdown/api/org/pegdown/LinkRenderer.html
;
;@pegdown-extensions
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

(def link-renderer
  "A pegdown LinkRenderer that renders wiki links as links to internal
  document fragments rather than external HTML pages."
  (proxy [LinkRenderer] []
    (render
      ([node]
       (if (instance? WikiLinkNode node)
         (try
           (LinkRenderer$Rendering.
             (str
               "#"
               (encode-anchor (.. node (getText))))
             (.getText node))
           (catch java.io.UnsupportedEncodingException _
             (throw (IllegalStateException.))))
         (proxy-super render node)))
      ([node text]
       (proxy-super render node text))
      ([node url title text]
       (proxy-super render node url title text)))))


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

(defn message [& s]
  (when (:verbose? *settings*)
    (binding [*out* (io/writer System/err)]
      (println (apply str s)))))

(defn each
  "Apply f to each item of coll in order for side effects only.
  Returns nil."
  [f coll]
  (doseq [c coll]
    (f c)))

(defn slurp-resource
  "Get the complete contents of a Java resource."
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
  Java resource."
  [path & continue-on-failure?]
  (try (slurp path)
    (catch java.io.FileNotFoundException _
      (message "Using internal " path)
      (slurp-resource path continue-on-failure?))))

(defn encode-anchor [s]
  (java.net.URLEncoder/encode (.replaceAll s "\\s" "-") "UTF-8"))

(defmacro with-out-stream [out & body]
  `(with-open [w# (io/writer ~out)]
     (binding [*out* w#]
       ~@body)))

(defn canonical-lang
  [l]
  (let [k (keyword l)]
    (get language-aliases k k)))

(defn lang-info
  [l]
  (languages (canonical-lang l)))

(defn lang-comment
  [l]
  (first (lang-info l)))

(defn lang-brush
  [l]
  (second (lang-info l)))


; ### Regular expression helpers

(defn comment
  "A regular expression matching the beginning of a commented line."
  []
  (re-pattern (str "^" (Pattern/quote *single-comment*) " ?")))

(defn anchor
  "A regular expression matching the beginning of an anchor line."
  []
  (re-pattern (str (comment) "@ *")))

(defn heading
  "A regular expression matching the beginning of a heading line."
  []
  (re-pattern (str (comment) "#+ *")))

(defn include
  "A regular expression matching the beginning of an include line."
  []
  (re-pattern (str (comment) "%include +")))


; ## Parsing


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

(defmulti code-anchor-id (fn [_ _] *language*) :default :default)

(defmethod code-anchor-id :clojure [line reader]
  (when (re-find #"^\(" line)
    (let [code (read reader)]
      (and (re-find #"^def" (str (first code)))
           (second code)))))

(defmethod code-anchor-id :vim [line reader]
  (second (re-find #"^function!?\s+([a-zA-Z_][a-zA-Z_0-9]+)" line)))

(defmethod code-anchor-id :default [_ _] nil)

(defn maybe-code-anchor [line reader]
  (if-let [id (code-anchor-id line reader)]
    [[:anchor (str *path* "/" id)]]
    []))

; Each line of a source file will be either code, comment, anchor, or include.
; Headings are treated as both anchors and comments.
(defn classify-line
  ""
  [[line :as lines]]
  (letfn [(match? [reg] (re-find (reg) line))]
    (cond-let
      [match]
      (match? include) [[:include (string/split
                                    (.substring line (count match)) #"\s+")]]
      (match? anchor) [[:anchor (.substring line (count match))]]
      (match? heading) [[:anchor (.substring line (count match))]
                        [:comment (.substring
                                    line (count (match? comment)))]]
      (match? comment) [[:comment (.substring line (count match))]]
      :else (conj (maybe-code-anchor line (lines-reader lines))
                  [:code line]))))

(defn classify-lines
  "Classify a line as :code or :comment. Return a pair of the classification
  and line string, sans leading comment tokens in the case of a comment."
  [lines]
  (loop [lines lines acc []]
    (if (seq lines)
      (recur (rest lines) (conj acc (classify-line lines)))
      (apply concat acc))))

; Adjacent lines of the same classification need to be gathered together into
; a single string except for anchors and includes.
(defn gather-lines- [lines]
  (lazy-seq
    (when-let [classfn (first (first lines))]
      (if (#{:comment :code} classfn)
        (let [[same tail] (split-with #(= classfn (first %)) lines)
              text (string/join "\n" (map second same))]
          (cons [classfn text] (gather-lines- tail)))
        (cons (first lines) (gather-lines- (rest lines)))))))

(defn gather-lines [lines]
  (gather-lines- (classify-lines lines)))

; The result of gathering is a list of pairs of the form
; `[<classification> <data>]`.
;
;
; ## Rendering
;
; The method that transforms each classified chunk into HTML, dispatches on the
; classification.
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
     (println (str "<" ~tag ">"))
     ~@body
     (println (str "</" ~tag ">"))))

(defn render-file [tag path & [token lang]]

; For each file, new bindings are made for comment syntax and language. If the
; language or comment syntax (`token`) are not supplied, they are guessed
; from the `path` suffix, and if that fails they are set to the values they
; have in the enclsing dynamic scope (i.e., from the commandline or the
; including file).
  (binding [*language* (canonical-lang
                         (or lang
                             (re-find #"(?<=\.)[^.]+$" path)
                             *language*))]
    (binding [*single-comment* (or token
                                   (lang-comment *language*)
                                   *single-comment*)]
      (binding [*path* path]
        (wrap-in-tags tag
          (if (#{:markdown :md} *language*)

; A markdown file is a special case. It is treated as a single comment block.
            (html<- [:comment (slurp path)])
            (do
              (maybe-associate-brush path *language*)
              (with-open [r (io/reader path)]

; Lines of source code are read lazily by `line-seq` and gathered lazily by
; `gather-lines`, Along with printing each chunk of comment or code to an
; output stream, this ensures that the maximum memory used by this program will
; be determined by the largest comment or code chunk and not the total size of
; the source file.
                (each html<- (gather-lines (line-seq r)))))))))))

; Code chunks are wrapped in a `pre` with the correct incantation for
; SyntaxHighlighter in its `class` attribute; comments are run through the
; Markdown processor; anchors become hrefless HTML anchors; and includes wrap
; the result of parsing and rendering the file they point to in `section` tags.

(defmethod html<- :code [[_ text]]
  (when-not (string/blank? text)
    (println (str "<pre class='brush: " (name *language*) "'>"
                  (string/escape
                    text
                    {\< "&lt;"
                     \> "&gt;"
                     \& "&amp;"})
                  "</pre>"))))

(defmethod html<- :comment [[_ text]]
  (println (.markdownToHtml processor text link-renderer)))

(defmethod html<- :anchor [[_ text]]
  (println (str "<a id='" (encode-anchor text) "'></a>")))

(defmethod html<- :include [[_ [path & [token lang]] :as args]]
  (let [lang (or lang (re-find #"(?<=\.)[^.]+$" path))]
    (render-file "section" path token lang)))


; ## Look and feel
;
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

; SyntaxHighlighter's defaults are not suitable, so they are tweaked.
(defn syntax-highlighter-setup []
  (inline-js
    (str "SyntaxHighlighter.defaults['toolbar'] = false;"
         "SyntaxHighlighter.defaults['smart-tabs'] = false;"
         "SyntaxHighlighter.defaults['gutter'] = false;"
         "SyntaxHighlighter.defaults['unindent'] = false;"
         "SyntaxHighlighter.defaults['class-name'] = 'code';"
         "SyntaxHighlighter.all();")))

; [h50](http://code.google.com/p/h5o/), the HTML5 outliner is used to create
; a table of contents.
(defn outliner-setup []
  (println "<div id=outline></div>")
  (inline-js
    (str "var outline = document.getElementById('outline');"
         "outline.innerHTML = HTML5Outline(document.body).asHTML(true);"
         "var children = outline.firstElementChild.firstElementChild.children;"
         "outline.removeChild(outline.firstElementChild);"
         "for (var i = 1; i < children.length; ++i) {"
         "  outline.appendChild(children[i]);"
         "}")))

; And here the two scripts are combined.
(defn javascript-setup []
  (syntax-highlighter-setup)
  (outliner-setup))
    

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
  (each (partial render-file "article") paths)
  (inline-brushes)
  (javascript-setup))

;@render-files
(defn render-files [paths]

; `*brushes*` is rebound on every invocation of `render-files`, because if it
; was not, unneeded brushes could accumulate and be included on subsequent
; calls to `render-files`.
  (binding [*brushes* (atom (or (set (:static-brushes *settings*)) #{})
                            :validator #(not (some (comp not string?) %)))]
    (render-files- paths)))

; The output stream can be a file or standard output or anything
; `clojure.java.io/writer` can handle.

;@process-files
(defn process-files
  "Take a list of file paths and an output stream, and render each file to the
  stream as HTML."
  [in-paths out]
  (with-out-stream out
    (render-files in-paths)))


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
    (if (or (not (seq tail)) (:help amap))
      (do (println (str usage "\n" banner))
        (System/exit 1))
      (binding [*settings* {:theme (or (:theme amap) (:theme *settings*))
                            :stylesheet (:stylesheet amap)
                            :static-brushes (:brush amap)
                            :verbose? (:verbose amap)}
                *single-comment* (:comment amap)
                *language* (canonical-lang (or (:language amap) *language*))]

; When no output file is given, the program renders to standard-out.
        (if (= 1 (count tail))
          (process-files tail *out*)
          (process-files (butlast tail) (last tail)))))))
