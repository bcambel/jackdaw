import listparser as lp
import sys

d = lp.parse(sys.argv[1])

for f in d.feeds:
    print f.url
