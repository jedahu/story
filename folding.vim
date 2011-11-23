" # Code folding for Vim
"
" Add this code to your `~/.vimrc` to enable folding of Markdown files and
" source files with Markdown comments.
"
" The fold level is determined by the number of `#` at the start of each
" heading. Underlined headings are not supported.
function! MarkdownLevel(token)
  let level = strlen(matchstr(getline(v:lnum),
        \                     '\(^'.a:token.'\)\@<=#\+\( \)\@='))
  if level == 0
    return "="
  else
    return ">".level
  endif
endfunction

" Different comment tokens are supported.
let g:comment#none = ''
let g:comment#semi = '; '
let g:comment#slashes = '// '
let g:comment#hash = '# '
let g:comment#dblquote = '" '

au BufEnter *.md,*.clj,*.cljs,*.js,*.sh setlocal foldmethod=expr

" I don't know why passing a string literal to `MarkdownLevel` doesn't work
" here. Vimscript is not my forte.
au BufEnter *.md setlocal foldexpr=MarkdownLevel(comment#none)
au BufEnter *.clj,*.cljs setlocal foldexpr=MarkdownLevel(comment#semi)
au BufEnter *.js setlocal foldexpr=MarkdownLevel(comment#slashes)
au BufEnter *.sh setlocal foldexpr=MarkdownLevel(comment#hash)
au BufEnter *.vim setlocal foldexpr=MarkdownLevel(comment#dblquote)


" ## Key bindings
"
" `za` toggle current fold  
" `zR` open all folds  
" `zM` close all folds  
" `zr` open one more level of folds  
" `zm` close one more level of folds  
