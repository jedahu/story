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
  
  (:gen-class))

; ## Utilities
;
; A few utility functions to make it easier to iterate side-effects through a
; collection, access resources, encode a string for use as an id or fragment
; identifier, and bind an output stream to `*out*` over a lexical scope.

(defn each
  "Apply f to each item of coll in order for side effects only. Returns nil."
  [f coll]
  (doseq [c coll]
    (f c)))

(defn slurp-resource
  "Get the complete contents of a Java resource."
  [resource-name]
  (-> (.getContextClassLoader (Thread/currentThread))
    (.getResourceAsStream resource-name)
    (java.io.InputStreamReader.)
    (slurp)))

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
; bound once for the lifetime of the program, so with the rest of the program
; they can also be considered constant.
;
; ### Parsing variables

(def ^:dynamic *single-comment*
  "A string containing a single comment token."
  ";")

(def comment
  "A regular expression matching the beginning of a commented line."
  (delay (re-pattern (str "^\\Q" *single-comment* "\\E ?"))))

(def anchor
  "A regular expression matching the beginning of an anchor line."
  (delay (re-pattern (str @comment "@ *"))))

(def include
  "A regular expression matching the beginning of an include line."
  (delay (re-pattern (str @comment "%include +"))))

; ### Style variables

(def ^:dynamic *brush*
  "Path to a SyntaxHighlighter brush file."
  nil)

(def ^:dynamic *theme*
  "Path to a SyntaxHighlighter theme file."
  nil)

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

; ## Parsing
;
; Each line of a source file will be either code, comment, anchor, or include.

(defn- classify-line
  "Classify a line as :code or :comment. Return a pair of the classification
  and line string, sans leading comment tokens in the case of a comment."
  [line]
  (if-let [s (re-find @include line)]
    [:include (.substring line (count s))]
    (if-let [s (re-find @anchor line)]
      [:anchor (.substring line (count s))]
      (if-let [s (re-find @comment line)]
        [:comment (.substring line (count s))]
        [:code line]))))

; Adjacent lines of the same classification need to be gathered together into
; a single string.

(defn- gather-lines- [lines]
  (lazy-seq
    (let [classfn (first (first lines))
          [same tail] (if (#{:comment :code} classfn)
                        (split-with #(= classfn (first %)) lines)
                        (split-at 1 lines))
          text (string/join "\n" (map second same))]
      (when classfn (cons [classfn text] (gather-lines- tail))))))

(defn- gather-lines [lines]
  (gather-lines- (map classify-line lines)))

; The result of gathering is a list of pairs of the form
; `[<classification> <string>]`.

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

(defn render-file [tag path]
  (with-open [r (io/reader path)]
    (println (str "<" tag ">"))
    (each html<- (gather-lines (line-seq r)))
    (println (str "</" tag ">"))))

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

(defmethod html<- :include [[_ path]]
  (render-file "section" path))

; ## Look and feel
;
; All javascript and css is inlined in the output.

(defn- inline-js [s]
  (println "<script>" s "</script>"))

(defn- inline-css [s]
  (println "<style>" s "</style>"))

; The default SyntaxHighlighter brush and theme (clojure and eclipse) can be
; overridden on the commandline, and an additional stylesheet can be included
; too.

(defn inline-brush []
  (inline-js
    (if *brush*
      (slurp *brush*)
      (slurp-resource "shBrushClojure.js"))))

(defn inline-theme []
  (inline-css
    (if *theme*
      (slurp *theme*)
      (slurp-resource "shThemeEclipse.css"))))

(defn inline-stylesheet []
  (when *stylesheet*
    (inline-css *stylesheet*)))

; The resulting look-and-feel resources are included together.

(defn look-and-feel []
  (inline-js (slurp-resource "XRegExp.js"))
  (inline-js (slurp-resource "shCore.js"))
  (inline-brush)
  (inline-theme)
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
  (syntax-highlighter-setup))

; The output stream can be a file or standard output or anything
; `clojure.java.io/writer` can handle.

(defn process-files [in-paths out]
  (with-out-stream out
    (render-files in-paths)))

; ## Commandline
;
; Use this program from the commandline like so.

(def usage "Usage: java -jar story.jar [options] <input-files>")

;@commandline entry point
(defn -main [& args]
  (let [[amap tail banner]
        (cli/cli args
                 ["-c" "--comment" "Comment syntax" :default ";"]
                 ["-b" "--brush" "SyntaxHighlighter brush file"]
                 ["-t" "--theme" "SyntaxHighlighter theme file"]
                 ["-l" "--language" "SyntaxHighlighter language"]
                 ["-s" "--stylesheet" "A stylesheet file to include"]
                 ["-h" "--help" "Show this help" :default false :flag true])]
    (if (or (not (seq tail)) (:help amap))
      (do (println usage banner)
        (System/exit 1))
      (binding [*single-comment* (:comment amap)
                *brush* (:brush amap)
                *theme* (:theme amap)
                *language* (or (:language amap) *language*)
                *stylesheet* (:stylesheet amap)]

; When no output file is given, the program renders to standard-out.

        (if (= 1 (count tail))
          (process-files tail *out*)
          (process-files (butlast tail) (last tail)))))))
