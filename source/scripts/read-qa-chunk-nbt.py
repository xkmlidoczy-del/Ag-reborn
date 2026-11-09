#!/usr/bin/env python3
"""Read one explicitly named closed QA Anvil chunk; never modifies the save."""
import argparse,gzip,json,pathlib,struct,zlib
from importlib.machinery import SourceFileLoader

Reader=SourceFileLoader('qa_nbt_reader',str(pathlib.Path(__file__).with_name('read-qa-world-nbt.py'))).load_module().Reader

def read_chunk(world,chunk_x,chunk_z):
    region=pathlib.Path(world)/'region'/f'r.{chunk_x//32}.{chunk_z//32}.mca'
    with region.open('rb') as stream:
        stream.seek(4*((chunk_x%32)+32*(chunk_z%32)))
        location=stream.read(4)
        if len(location)!=4:raise ValueError('Missing Anvil location')
        offset=int.from_bytes(location[:3],'big');sectors=location[3]
        if offset<2 or sectors==0:raise ValueError('QA chunk not saved')
        stream.seek(offset*4096)
        length=struct.unpack('>I',stream.read(4))[0]
        if length<2 or length>sectors*4096-4 or length>4_000_000:raise ValueError('Invalid bounded Anvil payload')
        compression=stream.read(1)[0];compressed=stream.read(length-1)
        if len(compressed)!=length-1:raise ValueError('Truncated Anvil payload')
    if compression==2:
        decoder=zlib.decompressobj();data=decoder.decompress(compressed,16_000_001)
        if not decoder.eof or decoder.unconsumed_tail:raise ValueError('Oversized or incomplete NBT')
    elif compression==1:data=gzip.decompress(compressed)
    elif compression==3:data=compressed
    else:raise ValueError('Unsupported Anvil compression')
    if len(data)>16_000_000:raise ValueError('Oversized chunk NBT')
    return Reader(data).root()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('world');parser.add_argument('chunk_x',type=int);parser.add_argument('chunk_z',type=int)
    args=parser.parse_args();print(json.dumps(read_chunk(args.world,args.chunk_x,args.chunk_z),indent=2))
