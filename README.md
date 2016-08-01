# Running

## Dribbble stats

```
lein repl
(require '[att.dribbble-stats :refer [run]])
(run "EgorKovalchuk")
```

```
"Elapsed time: 797575.629998 msecs"
({:id 469578, :username "an_nastiia", :likes 346} {:id 1011295, :username "qpoziomek", :likes 226} {:id 698, :username "dperrera", :likes 166} {:id 1164473, :username "Omninoscom", :likes 162} {:id 656025, :username "judag", :likes 157} {:id 73241, :username "leoleung", :likes 155} {:id 108972, :username "disbag", :likes 154} {:id 132100, :username "truemarmalade", :likes 145} {:id 1051028, :username "uixrepubluc", :likes 144} {:id 298242, :username "LS5", :likes 134})
```

## URL Matcher

```
lein repl
(require '[att.matcher :refer [recognize]])
(import '[att.matcher Pattern])
(def twitter (Pattern. "host(twitter.com); path(?user/status/?id);"))
(recognize twitter "http://twitter.com/bradfitz/status/562360748727611392")
```
