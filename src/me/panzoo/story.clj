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
    [org.pegdown PegDownProcessor Extensions

; Internal links (to element ids) are nice to have. These imports will be used
; to create a modified LinkRenderer.

     LinkRenderer LinkRenderer$Rendering]
    [org.pegdown.ast WikiLinkNode])

; To run from the commandline this namespace must be compiled with a `-main`
; method. When run from the REPL or from Leiningen `gen-class` does nothing.
  
  (:gen-class))

; ## Utilities
;
; A few utility functions to make it easier to print messages to the console,
; iterate side-effects through a collection, access resources, encode a string
; for use as an id or fragment identifier, and bind an output stream to `*out*`
; over a lexical scope.

(defn message [& s]
  (binding [*out* (io/writer System/err)]
    (println (apply str s))))

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
      (message "failed to read resource " resource-name)
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
      (message path " not found; reading as a resource instead")
      (slurp-resource path continue-on-failure?))))

(defn encode-anchor [s]
  (java.net.URLEncoder/encode (.replace s " " "-") "UTF-8"))

(defmacro with-out-stream [out & body]
  `(with-open [w# (io/writer ~out)]
     (binding [*out* w#]
       ~@body)))

; ## Top level variables
;
; A few objects need to be accessed by a number of functions: regular
; expressions that match the beginning of commented lines, SyntaxHighlighter
; options, a PegDownProcessor, and a modified LinkRenderer. Of these, the
; processor and renderer are never dynamically bound. The other variables are
; bound once for the lifetime of the program, so for the rest of the program
; they can be considered constant.
;
; ### Parsing variables

(def ^:dynamic *single-comment*
  "A string containing a single comment token."
  ";")

(defn comment
  "A regular expression matching the beginning of a commented line."
  []
  (re-pattern (str "^\\Q" *single-comment* "\\E ?")))

(defn anchor
  "A regular expression matching the beginning of an anchor line."
  []
  (re-pattern (str (comment) "@ *")))

(defn include
  "A regular expression matching the beginning of an include line."
  []
  (re-pattern (str (comment) "%include +")))

; ### Style variables

;@language map
(def languages
  "A map of language names to a pairs of comment syntax and SyntaxHighlighter
  brush file names."
  {"clojure" [";" "shBrushClojure.js"]
   "clj" [";" "shBrushClojure.js"]
   "cljs" [";" "shBrushClojure.js"]
   "applescript" ["--" "shBrushAppleScript.js"]
   "actionscript3" ["//" "shBrushAS3.js"]
   "as3" ["//" "shBrushAS3.js"]
   "bash" ["#" "shBrushBash.js"]
   "shell" ["#" "shBrushBash.js"]
   "sh" ["#" "shBrushBash.js"]
   ;"coldfusion" "shBrushColdFusion.js"
   "cpp" ["//" "shBrushCpp.js"]
   "c++" ["//" "shBrushCpp.js"]
   "cxx" ["//" "shBrushCpp.js"]
   "c" ["//" "shBrushCpp.js"]
   "c#" ["//" "shBrushCSharp.js"]
   "c-sharp" ["//" "shBrushCSharp.js"]
   "csharp" ["//" "shBrushCSharp.js"]
   "delphi" ["//" "shBrushDelphi.js"]
   "pascal" ["//" "shBrushDelphi.js"]
   ;"diff" "shBrushDiff.js"
   ;"patch" "shBrushDiff.js"
   "erlang" ["%" "shBrushErlang.js"]
   "erl" ["%" "shBrushErlang.js"]
   "groovy" ["//" "shBrushGroovy.js"]
   "java" ["//" "shBrushJava.js"]
   "javafx" ["//" "shBrushJavaFX.js"]
   "jfx" ["//" "shBrushJavaFX.js"]
   "javascript" ["//" "shBrushJScript.js"]
   "js" ["//" "shBrushJScript.js"]
   "perl" ["#" "shBrushPerl.js"]
   "pl" ["#" "shBrushPerl.js"]
   "php" ["#" "shBrushPhp.js"]
   ;"text" "shBrushPlain.js"
   ;"txt" "shBrushPlain.js"
   ;"plain" "shBrushPlain.js"
   "python" ["#" "shBrushPython.js"]
   "py" ["#" "shBrushPython.js"]
   "ruby" ["#" "shBrushRuby.js"]
   "rb" ["#" "shBrushRuby.js"]
   "sass" ["//" "shBrushSass.js"]
   "scss" ["//" "shBrushSass.js"]
   "scala" ["//" "shBrushScala.js"]
   "sql" ["--" "shBrushSql.js"]
   "vb" ["'" "shBrushVb.js"]
   "vbnet" ["'" "shBrushVb.js"]
   ;"xml" "shBrushXml.js"
   ;"xhtml" "shBrushXml.js"
   ;"xslt" "shBrushXml.js"
   ;"html" "shBrushXml.js"
   })

(def ^:dynamic *theme*
  "Path to a SyntaxHighlighter theme file."
  "shThemeEclipse.css")

(def ^:dynamic *language*
  "A language string for use with SyntaxHighlighter."
  "clojure")

(def ^:dynamic *stylesheet*
  "Path to a stylesheet file."
  nil)

; ### Pegdown variables

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

; ### Mutable variables

(def brushes (atom #{}))

; ## Parsing
;
; Each line of a source file will be either code, comment, anchor, or include.

(defn- classify-line
  "Classify a line as :code or :comment. Return a pair of the classification
  and line string, sans leading comment tokens in the case of a comment."
  [line]
  (if-let [s (re-find (include) line)]
    [:include (string/split (.substring line (count s)) #"\s+")]
    (if-let [s (re-find (anchor) line)]
      [:anchor (.substring line (count s))]
      (if-let [s (re-find (comment) line)]
        [:comment (.substring line (count s))]
        [:code line]))))

; Adjacent lines of the same classification need to be gathered together into
; a single string.

(defn- gather-lines- [lines]
  (lazy-seq
    (when-let [classfn (first (first lines))]
      (if (#{:comment :code} classfn)
        (let [[same tail] (split-with #(= classfn (first %)) lines)
              text (string/join "\n" (map second same))]
          (cons [classfn text] (gather-lines- tail)))
        (cons (first lines) (gather-lines- (rest lines)))))))

(defn- gather-lines [lines]
  (gather-lines- (map classify-line lines)))

; The result of gathering is a list of pairs of the form
; `[<classification> <data>]`.

; ## Rendering
;
; The method that transforms each classified
; chunk into HTML, dispatches on the classification.

(defmulti html<- first)

; Lines of source code are read lazily by `line-seq` and gathered lazily by
; `gather-lines`, Along with printing each chunk of comment or code to an
; output stream, this ensures that the maximum memory used by this program will
; be determined by the largest comment or code chunk and not the total size of
; the source file.

(defn render-file [tag path & [token lang]]
  (let [lang (or lang (re-find #"(?<=\.)[^.]+$" path))
        token (or token (first (languages lang)))]
    (when-let [b (and lang (second (languages lang)))]
      (message path " auto-associated with brush " b)
      (swap! brushes conj b))
    (with-open [r (io/reader path)]
      (binding [*single-comment* (or token *single-comment*)
                *language* (or lang *language*)]
        (println (str "<" tag ">"))
        (each html<- (gather-lines (line-seq r)))
        (println (str "</" tag ">"))))))

; Code chunks are wrapped in a `pre` with the correct incantation for
; SyntaxHighlighter in its `class` attribute; comments are run through the
; Markdown processor; anchors become hrefless HTML anchors; and includes wrap
; the result of parsing and rendering the file they point to in `section` tags.

(defmethod html<- :code [[_ text]]
  (when-not (string/blank? text)
    (println (str "<div class=code><pre class='brush: " *language* "'>")
             (string/escape
               text
               {\< "&lt;"
                \> "&gt;"
                \& "&amp;"})
             "</pre></div>")))

(defmethod html<- :comment [[_ text]]
  (println (.markdownToHtml processor text link-renderer)))

(defmethod html<- :anchor [[_ text]]
  (println (str "<a id='" (encode-anchor text) "'></a>")))

(defmethod html<- :include [[_ [path & [token lang]] :as args]]
  (let [lang (or lang (re-find #"(?<=\.)[^.]+$" path))]
    (render-file "section" path token lang)))

; ## Look and feel
;
; All javascript and CSS is inlined in the output.

(defn- inline-js [s]
  (println "<script>" s "</script>"))

(defn- inline-css [s]
  (println "<style>" s "</style>"))

; The default SyntaxHighlighter brush and theme (clojure and eclipse) can be
; overridden on the commandline, and an additional stylesheet can be included
; too.

(defn inline-brushes []
  (message "Adding the following brushes to output:")
  (each (partial message "    ") @brushes)
  (each (comp inline-js #(slurp-file|resource % :continue-on-failure))
        @brushes))

(defn inline-theme []
  (inline-css (slurp-file|resource *theme* :continue-on-faliure)))

(defn inline-stylesheet []
  (when *stylesheet*
    (inline-css *stylesheet*)))

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

(defn- render-files [paths]
  (println
    "<!doctype html>"
    "<meta charset=utf-8>")
  (look-and-feel)

; Multiple input files are rendered to a single stream. The content of each
; file's rendering is wrapped in its own `article` tags.

  (each (partial render-file "article") paths)
  (inline-brushes)
  (javascript-setup))

; The output stream can be a file or standard output or anything
; `clojure.java.io/writer` can handle.

(defn process-files [in-paths out]
  (with-out-stream out
    (render-files in-paths)))

;@commandline
;
; ## Commandline
;
; Use this program from the commandline like so.

(def usage "Usage: java -jar story.jar [options] <input-files>")

(defn -main [& args]
  (let [[amap tail banner]
        (cli/cli args

; For the brush, theme, and stylesheet options the program first tries to read
; from the filesystem and if that fails because the file is not found the
; an attempt is made to read from the program's resources. The resources
; contain the standard SyntaxHighlighter brushes and themes under their normal
; file names including an additional `shBrushClojure.js` and
; `shThemeClojure.css`.
;
; Multiple brushes can be used by repeating the `-b` or `--brush` options.
; Additional brushes may be added by the program as a result of file includes
; and file suffixes.

                 ["-c" "--comment" "Comment syntax" :default ";"]
                 ["-b" "--brush" "SyntaxHighlighter brush file" :multi true]
                 ["-t" "--theme" "SyntaxHighlighter theme file"]
                 ["-l" "--language" "SyntaxHighlighter language"]
                 ["-s" "--stylesheet" "A stylesheet file to include"]
                 ["-h" "--help" "Show this help" :default false :flag true])]
    (if (or (not (seq tail)) (:help amap))
      (do (println usage banner)
        (System/exit 1))
      (binding [*single-comment* (:comment amap)
                *theme* (:theme amap)
                *language* (or (:language amap) *language*)
                *stylesheet* (:stylesheet amap)]
        (swap! brushes #(or (set (:brush amap)) %))

; When no output file is given, the program renders to standard-out.

        (if (= 1 (count tail))
          (process-files tail *out*)
          (process-files (butlast tail) (last tail)))))))
