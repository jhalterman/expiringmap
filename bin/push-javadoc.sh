#!/bin/sh
# run from top level dir
rm -rf docs
git clone git@github.com:jhalterman/expiringmap.git docs -b gh-pages
mvn -Pjavadoc javadoc:javadoc
cd docs
git add -A
git commit -m "Updated JavaDocs"
git push origin gh-pages