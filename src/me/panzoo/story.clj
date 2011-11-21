; # A literate programming story
;
; This is the story of a small program that parses other programs. It converts
; their comments into HTML (using the [Pegdown][1] implementation of
; [Markdown][2]) and nicely highlights their code
; (using [SyntaxHighlighter][3]).
;
; [1]: https://github.com/sirthias/pegdown
; [2]: http://daringfireball.net/projects/markdown/
; [3]: http://alexgorbatchev.com/SyntaxHighlighter/
;
; The usual stuff comes first. We need to read and write to files, manipulate
; strings, and parse Markdown.

(ns me.panzoo.story
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string])
  (:import
    [org.pegdown PegDownProcessor Extensions]))

; A couple of utility functions to make it easier to iterate side-effects
; through a collection and access resources.

(defn- each
  "Apply f to each item of coll in order for side effects only. Returns nil."
  [f coll]
  (doseq [c coll]
    (f c)))

(defn slurp-resource
  "Stolen from marginalia."
  [resource-name]
  (-> (.getContextClassLoader (Thread/currentThread))
    (.getResourceAsStream resource-name)
    (java.io.InputStreamReader.)
    (slurp)))

; Two objects need to be accessed by a number of functions: a regular
; expression that matches the beginning of a commented line, and a
; PegDownProcessor. Neither object will be mutated after the initial setup.

(def ^:dynamic *single-comment* #"^; ?")
(def ^:dynamic *processor* nil)

; Each line of a source file will be either code or comment.

(defn- classify-line
  "Classify a line as :code or :comment. Return a pair of the classification
  and line string, sans leading comment tokens in the case of a comment."
  [line]
  (if-let [s (re-find *single-comment* line)]
    [:comment (.substring line (count s))]
    [:code line]))

; Adjacent lines of the same classification need to be gathered together into
; a single string.

(defn- gather-lines- [lines]
  (lazy-seq
    (let [classfn (first (first lines))
          [same tail] (split-with #(= classfn (first %)) lines)
          text (string/join "\n" (map second same))]
      (when classfn (cons [classfn text] (gather-lines- tail))))))

(defn- gather-lines [lines]
  (gather-lines- (map classify-line lines)))

; The result of gathering is a list of pairs of the form
; `[<classification> <string>]`. The method that transforms each classified
; chunk into HTML, dispatches on the classification.

(defmulti html<- first)

; Comments are run through the Markdown processor and then wrapped in a
; `section`.

(defmethod html<- :comment [[_ text]]
  (str "<section class='comment'>\n"
       (.markdownToHtml *processor* text)
       "</section>\n"))

; Code chunks are wrapped in a `pre` with the correct incantation for
; SyntaxHighlighter in its `class` attribute.

(defmethod html<- :code [[_ text]]
  (string/join "\n" ["<div class='code'><pre class='brush: clojure'>"
                     (string/escape
                       text
                       {\< "&lt;"
                        \> "&gt;"
                        \& "&amp;"})
                     "</pre></div>"]))

; The SyntaxHighlighter code is inlined into the output HTML.

(defn- inline-js [path]
  (str "<script>\n" (slurp-resource path) "</script>"))

(defn- inline-css [path]
  (str "<style>\n" (slurp-resource path) "</style>"))

; Code, comments, and supporting HTML are written to an output file.

(defn- render-to-file [sections path]
  (with-open [w (io/writer path)]
    (binding [*out* w]
      (each
        println
        ["<!doctype html>"
         "<meta charset=utf-8>"
         (inline-js "XRegExp.js")
         (inline-js "shCore.js")
         (inline-js "shBrushClojure.js")
         (inline-css "shThemeEclipse.css")
         (inline-css "shClojureExtra.css")
         "<style>"
         "div.code { background-color: #f5f5ff }"
         "div.code { border: solid #e5e5ee 1px }"
         "</style>"])
      (each println (map html<- sections))

; SyntaxHighlighter's defaults are not suitable, so they get tweaked.

      (each
        println
        ["<script>"
         "SyntaxHighlighter.defaults['toolbar'] = false;"
         "SyntaxHighlighter.defaults['smart-tabs'] = false;"
         "SyntaxHighlighter.defaults['gutter'] = false;"
         "SyntaxHighlighter.defaults['unindent'] = false;"
         "SyntaxHighlighter.all();"
         "</script>"]))))

; Lines of source code are read lazily by `line-seq` and gathered lazily by
; `gather-lines`; along with printing each chunk of comment or code to an
; output stream, this ensures that the maximum memory used by this program will
; be determined by the largest comment or code chunk and not the total size of
; the source file.

(defn process-file [in-path out-path]
  (with-open [r (io/reader in-path)]
      (render-to-file (gather-lines (line-seq r)) out-path)))

(defmacro with-processor [& body]
  `(binding [*processor* (PegDownProcessor. (bit-or
                                              (. Extensions AUTOLINKS)
                                              (. Extensions SMARTYPANTS)))]
     ~@body))
