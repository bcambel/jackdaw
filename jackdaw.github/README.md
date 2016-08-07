# jackdaw.github

FIXME: description

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

```clojure
;; define a temp repository list
(def repos (atom []))

(defn update-repos
  [login reps]
  (swap! repos concat reps))

(user-repos "bcambel" 10 update-repos)
;; lets find out all the clojure repos that
;; I am watching...
(def clojure-repos (filter #(= (:language %) "Clojure") @repos))


```

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
