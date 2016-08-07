# jackdaw.youtube

Download Youtube videos using https://github.com/rg3/youtube-dl

## Installation

```bash
sudo add-apt-repository ppa:mc3man/trusty-media
sudo apt-get update
sudo apt-get install ffmpeg
```

## Usage

```bash
ffmpeg -i Clojure\ Data\ Structures\ Part\ 1\ -\ Rich\ Hickey-ketJlzX-254.mkv -c copy -map 0 -segment_time 32 -f segment clojure_data_part1_%03d.mkv
```

which will separate the video file into multiple segments of similar sizes and can be playable individually.


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
