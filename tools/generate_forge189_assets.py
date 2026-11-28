"""Build the independent 1.8.9 edition's resources and offline UV preview.

Uses local Minecraft art as the bevel/UV reference; never downloads or bundles
the game JAR. Modern Warden Pact resources are read only.
"""
from pathlib import Path
import io
import json
import os
import shutil
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import refresh_textures as preview

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'forge-1.8.9/src/main/resources/assets/witherpact'
BACKUP = ROOT / '.tools/forge189/resource-backup'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        backup = BACKUP / path.relative_to(RES)
        if not backup.exists():
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup)
    temp = path.with_name(path.name + '.tmp')
    temp.write_bytes(data)
    os.replace(temp, path)

def png(path, im):
    out = io.BytesIO(); im.save(out, format='PNG', optimize=True); write(path, out.getvalue())

def document(path, data):
    write(path, (json.dumps(data, ensure_ascii=False, indent=2)+'\n').encode())

def alloy(im):
    im = im.copy().convert('RGBA')
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a = im.getpixel((x,y))
            if not a: continue
            v = .25*r+.55*g+.20*b
            if g > r*1.20 and g > 55:  # Cyan inlays become amethyst.
                color = (min(255,int(v*1.08+15)), int(v*.53+8), min(255,int(v*1.32+30)), a)
            elif r > b*1.12 and r > 75:  # Existing bone details remain warm.
                color = (min(255,int(v*1.1+8)), min(255,int(v*1.05+6)), int(v*.88+7), a)
            else:
                color = (int(v*.91+3), int(v*.83+2), int(v*1.10+8), a)
            im.putpixel((x,y), color)
    return im

ZH = {
 'itemGroup.witherpact':'凋零契约',
 'item.witherpact.echo_shard.name':'凋零回响碎片',
 'item.witherpact.summoning_sword.name':'凋零契约剑',
 'item.witherpact.wither_reins.name':'凋零缰绳',
 'item.witherpact.echo_helmet.name':'冥骨头盔',
 'item.witherpact.echo_chestplate.name':'冥骨胸甲',
 'item.witherpact.echo_leggings.name':'冥骨护腿',
 'item.witherpact.echo_boots.name':'冥骨战靴',
 'item.witherpact.echo_pickaxe.name':'冥骨镐',
 'item.witherpact.echo_axe.name':'冥骨斧',
 'item.witherpact.echo_shovel.name':'冥骨锹',
 'item.witherpact.echo_hoe.name':'冥骨锄',
 'tile.witherpact.echo_path.name':'契约土径',
 'item.witherpact.summoning_sword.ability':'命中敌对生物：召唤友方凋零，持续60秒，冷却20秒',
 'item.witherpact.wither_reins.ability':'右键召唤/召回飞行凋零；潜行右键遣散；碎片治疗',
 'item.witherpact.echo_helmet.ability':'佩戴持续夜视；潜行侦测16格内敌人，冷却8秒',
 'item.witherpact.echo_chestplate.ability':'受到近身敌人伤害：回响震击并击退，冷却8秒',
 'item.witherpact.echo_leggings.ability':'穿戴时移动速度 +15%',
 'item.witherpact.echo_boots.ability':'穿戴时摔落伤害 -50%',
 'item.witherpact.echo_pickaxe.ability':'潜行右键：连锁挖掘同类矿脉，最多16块，冷却2秒',
 'item.witherpact.echo_axe.ability':'潜行右键：连锁砍伐同类原木，最多16块，冷却2秒',
 'item.witherpact.echo_shovel.ability':'右键开路；潜行右键开辟3×3土径，冷却2秒',
 'item.witherpact.echo_hoe.ability':'潜行右键：耕作3×3土地，冷却2秒',
 'enchantment.witherpact.contract_resonance':'契约共鸣',
 'enchantment.witherpact.sculk_guard':'幽匿守护',
 'enchantment.witherpact.echo_mining':'回响采掘',
 'entity.witherpact.pact_wither.name':'契约凋零',
 'entity.witherpact.pact_skull.name':'契约凋零之首',
 'key.witherpact.descend':'凋零坐骑下降',
 'key.categories.witherpact':'凋零契约',
 'message.witherpact.not_yours':'这只凋零只听从它的契约主人。',
 'message.witherpact.no_space':'周围没有足够空间；请到更开阔的地方召唤。',
 'message.witherpact.dismount_first':'请先离开坐骑，再遣散凋零。',
 'message.witherpact.mount_ready':'凋零坐骑已就绪：右键骑乘；空格上升，X下降，Shift下坐骑。',
}
EN = {
 'itemGroup.witherpact':'Wither Pact',
 'item.witherpact.echo_shard.name':'Wither Echo Shard',
 'item.witherpact.summoning_sword.name':'Wither Pact Sword',
 'item.witherpact.wither_reins.name':'Wither Reins',
 'tile.witherpact.echo_path.name':'Pact Path',
 'enchantment.witherpact.contract_resonance':'Contract Resonance',
 'enchantment.witherpact.sculk_guard':'Sculk Guard',
 'enchantment.witherpact.echo_mining':'Echo Mining',
 'entity.witherpact.pact_wither.name':'Pact Wither',
 'entity.witherpact.pact_skull.name':'Pact Wither Skull',
 'key.witherpact.descend':'Descend on Wither',
 'key.categories.witherpact':'Wither Pact',
 'message.witherpact.not_yours':'This wither belongs to another player.',
 'message.witherpact.no_space':'Not enough room nearby. Move to an open area.',
 'message.witherpact.dismount_first':'Dismount before dismissing the wither.',
 'message.witherpact.mount_ready':'Wither ready: right-click to ride; Space up, X down, Shift dismount.',
 'item.witherpact.summoning_sword.ability':'Hit a hostile: summon a friendly wither for 60s; cooldown 20s',
 'item.witherpact.wither_reins.ability':'Right-click: summon/recall; sneak-use: dismiss; feed shards to heal',
 'item.witherpact.echo_helmet.ability':'Continuous night vision while worn; sneak-sense hostiles within 16 blocks (8s cooldown)',
 'item.witherpact.echo_chestplate.ability':'Nearby hostile damage triggers a 4-damage repelling pulse (8s cooldown)',
 'item.witherpact.echo_leggings.ability':'+15% movement speed while worn',
 'item.witherpact.echo_boots.ability':'50% less fall damage while worn',
 'item.witherpact.echo_pickaxe.ability':'Sneak-use: mine connected matching ores, up to 16 blocks (2s cooldown)',
 'item.witherpact.echo_axe.ability':'Sneak-use: fell connected matching logs, up to 16 blocks (2s cooldown)',
 'item.witherpact.echo_shovel.ability':'Use: make a path; sneak-use: 3x3 path (2s cooldown)',
 'item.witherpact.echo_hoe.ability':'Sneak-use: till 3x3 farmland (2s cooldown)',
}
for item,label in {'helmet':'Helmet','chestplate':'Chestplate','leggings':'Leggings','boots':'Boots','pickaxe':'Pickaxe','axe':'Axe','shovel':'Shovel','hoe':'Hoe'}.items():
    EN[f'item.witherpact.echo_{item}.name']='Dreadbone '+label

def main():
    icons={}
    source=ROOT/'src/main/resources/assets/wardenpact/textures'
    for name in ('echo_shard','summoning_sword','echo_helmet','echo_chestplate','echo_leggings','echo_boots','echo_pickaxe','echo_axe','echo_shovel','echo_hoe'):
        if name=='echo_shard':
            with zipfile.ZipFile(ROOT/'.tools/minecraft-26.3.jar') as z:
                im=alloy(Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/echo_shard.png'))))
        else:
            im=alloy(Image.open(source/'item'/f'{name}.png'))
        # Bone-white central crests distinguish these from the modern cyan set.
        points={'echo_helmet':[(7,3),(8,3),(7,4)],'echo_chestplate':[(7,6),(8,6),(7,7),(8,7)],
                'summoning_sword':[(11,3),(10,4),(9,5)],'echo_pickaxe':[(5,3),(6,3)],'echo_axe':[(4,4),(5,4)]}.get(name,[])
        for p in points:
            if im.getpixel(p)[3]: im.putpixel(p,(214,207,183,255))
        png(RES/'textures/items'/f'{name}.png',im); icons[name]=im
    # A three-skull-shaped amulet joined to leather reins, original pixel art.
    im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    d.line([(2,3),(2,11),(5,14),(11,14),(14,11),(14,3)],fill='#19141f',width=2)
    d.line([(3,4),(3,10),(6,13),(10,13),(13,10),(13,4)],fill='#775246',width=1)
    for x,y in ((6,2),(2,4),(10,4)):
        d.rectangle((x,y,x+3,y+3),fill='#282230',outline='#b7af9b')
        d.point((x+1,y+1),fill='#c385ff'); d.point((x+2,y+1),fill='#7b4aa0')
    d.rectangle((7,7,8,10),fill='#b283d5'); d.point((7,7),fill='#e3ccff')
    png(RES/'textures/items/wither_reins.png',im); icons['wither_reins']=im
    armor={}
    for old,new in (('humanoid','echo_layer_1'),('humanoid_leggings','echo_layer_2')):
        im=alloy(Image.open(source/'entity/equipment'/old/'echo.png'))
        # Thick bone trim across the visor and chest plate; alpha openings stay intact.
        for x,y in ([(x,8) for x in range(8,16)]+[(x,20) for x in range(20,28)]):
            if im.getpixel((x,y))[3]: im.putpixel((x,y),(183,176,158,255))
        png(RES/'textures/models/armor'/f'{new}.png',im); armor[old]=im
    with zipfile.ZipFile(ROOT/'.tools/minecraft-26.3.jar') as z:
        skin=Image.open(io.BytesIO(z.read('assets/minecraft/textures/entity/wither/wither.png'))).convert('RGBA')
        skin=alloy(skin)
        d=ImageDraw.Draw(skin)
        # ModelWither uses center head front (8,8)-(15,15), side heads (4,4)-(9,9).
        d.rectangle((8,8,15,15),fill='#35303c')
        for x in (9,13): d.rectangle((x,10,x+1,11),fill='#c994ff'); d.point((x,10),fill='#f2dcff')
        d.line([(10,13),(13,13)],fill='#c7c0ab',width=1)
        d.line([(8,8),(15,8)],fill='#a69e8c')
        d.rectangle((5,5,6,5),fill='#bb81e8'); d.rectangle((8,5,9,5),fill='#bb81e8')
        for y in (17,20,23): d.line([(6,y),(15,y)],fill='#a79e8b')
        png(RES/'textures/entity/pact_wither.png',skin)
        saddle=skin.copy(); d=ImageDraw.Draw(saddle)
        d.rectangle((2,16,5,25),fill='#57404d'); d.line([(3,16),(3,25)],fill='#b6a47d')
        d.rectangle((8,16,11,18),fill='#37293d',outline='#b7a58b')
        png(RES/'textures/entity/wither_mount.png',saddle)
        for name,ref in (('path_top','dirt_path_top'),('path_side','dirt_path_side'),('dirt','dirt')):
            png(RES/'textures/blocks'/f'{name}.png',Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/block/{ref}.png'))).convert('RGBA'))
    # 1.8.9 uses builtin/generated; handheld transforms are explicit.
    for name in icons:
        model={'parent':'builtin/generated','textures':{'layer0':f'witherpact:items/{name}'}}
        if name=='summoning_sword' or name in ('echo_pickaxe','echo_axe','echo_shovel','echo_hoe'):
            model['display']={'thirdperson':{'rotation':[-90,0,0],'translation':[0,1,-3],'scale':[.55,.55,.55]},
                              'firstperson':{'rotation':[0,-135,25],'translation':[0,4,2],'scale':[1.7,1.7,1.7]}}
        document(RES/'models/item'/f'{name}.json',model)
    document(RES/'blockstates/echo_path.json',{'variants':{'normal':{'model':'witherpact:echo_path'}}})
    textures={'top':'witherpact:blocks/path_top','side':'witherpact:blocks/path_side','bottom':'witherpact:blocks/dirt','particle':'witherpact:blocks/path_top'}
    faces={name:{'texture':'#'+('top' if name=='up' else 'bottom' if name=='down' else 'side'),'cullface':name} for name in ('up','down','north','south','east','west')}
    document(RES/'models/block/echo_path.json',{'textures':textures,'elements':[{'from':[0,0,0],'to':[16,15,16],'faces':faces}]})
    document(RES/'models/item/echo_path.json',{'parent':'witherpact:block/echo_path','display':{'thirdperson':{'rotation':[10,-45,170],'translation':[0,1.5,-2.75],'scale':[.375,.375,.375]}}})
    for locale,entries in (('en_US',EN),('zh_CN',ZH)):
        write(RES/'lang'/f'{locale}.lang',(''.join(f'{key}={value}\n' for key,value in sorted(entries.items()))).encode())
    make_preview(icons,armor,saddle)
    print(json.dumps({'textures':len(list((RES/'textures').rglob('*.png'))),'items':len(icons),'locales':2,'preview':str(ROOT/'docs/wither-forge-1.8.9-preview.png')},ensure_ascii=False))

def make_preview(icons,armor,skin):
    sheet=Image.new('RGB',(1280,990),'#17131e');d=ImageDraw.Draw(sheet)
    regular=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
    small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',15)
    title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',30)
    d.text((32,22),'凋零契约 · 黑曜冥骨',font=title,fill='#e8dfca')
    d.text((34,68),'MINECRAFT 1.8.9 / FORGE     |     紫色灵魂嵌纹 · 骨白护边 · 厚重金属倒角',font=small,fill='#b19dbf')
    for i,(name,im) in enumerate(icons.items()):
        row,col=divmod(i,6);x,y=32+col*205,108+row*143
        d.rounded_rectangle((x,y,x+194,y+132),6,fill='#292230')
        sprite=im.resize((80,80),Image.Resampling.NEAREST);sheet.paste(sprite,(x+57,y+8),sprite)
        label=ZH['item.witherpact.'+name+'.name'];d.text((x+20,y+96),label,font=regular,fill='#d6cddd')
    for i,yaw in enumerate((-.18,-.72,2.65)):
        im=preview.render(preview.armor_model(armor),(230,385),yaw=yaw,pitch=.12)
        sheet.paste(im,(24+i*229,446),im)
    faces=[]
    for uv,pos,size in [((0,0),(-4,-16,-4),(8,8,8)),((0,0),(-13,-13,-3),(6,6,6)),((0,0),(7,-13,-3),(6,6,6)),
                        ((0,16),(-10,-7,-1.5),(20,3,3)),((0,16),(-1.5,-4,-1.5),(3,10,3)),
                        ((0,16),(-5,-3,-1.5),(10,2,3)),((0,16),(-5,1,-1.5),(10,2,3)),((0,16),(-5,5,-1.5),(10,2,3)),
                        ((12,22),(-1.5,6,-1.5),(3,6,3))]:
        faces.extend(preview.box_faces(skin,uv,pos,size))
    im=preview.render(faces,(510,385),yaw=-.48,pitch=.12);sheet.paste(im,(730,450),im)
    d.text((92,857),'正面',font=regular,fill='#c6b7d1');d.text((315,857),'侧前',font=regular,fill='#c6b7d1');d.text((545,857),'背面',font=regular,fill='#c6b7d1')
    d.text((837,857),'飞行凋零坐骑 · 紫瞳与骨纹',font=regular,fill='#c6b7d1')
    d.text((32,925),'穿戴与坐骑为离线 UV 示意预览，游戏内姿态、光照和附魔光效以实际运行效果为准。',font=small,fill='#9987a6')
    d.text((32,951),'空格上升 / X下降 / Shift离开坐骑       ·       头盔持续夜视，其他装备保留独立能力',font=small,fill='#b29dc1')
    sheet.save(ROOT/'docs/wither-forge-1.8.9-preview.png')

if __name__=='__main__': main()
