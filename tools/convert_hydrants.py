"""서울시 소방용수시설(OA-21306) 원본 CSV(cp949)를 앱용 assets/hydrants.csv 로 바꾼다.

사용: python3 -I tools/convert_hydrants.py <원본.csv> app/src/main/assets/hydrants.csv
시설유형코드 정의(같은 데이터셋의 '소방용수시설 표준등록 현행화_2026.xlsx' 머리글):
  01 지상식소화전 / 02 지하식소화전 / 03 급수탑 / 04 저수조 / 05 지하식상승식
"""
import csv, io, re, sys

TYPE = {"1": "GROUND", "2": "UNDERGROUND", "3": "TOWER", "4": "RESERVOIR", "5": "UNDERGROUND"}
JUNK = {"", "N", "Y", "없음", "있음", "현장대응단"}
NUM = re.compile(r"^\d{2,3}\.\d+$")


def clean(s):
    return re.sub(r"\s+", " ", (s or "").replace(",", " ").replace("�", "")).strip()


def main(src, dst):
    text = open(src, "rb").read().decode("cp949", errors="replace")
    rows = csv.DictReader(io.StringIO(text))
    kept = dropped = 0
    with open(dst, "w", encoding="utf-8", newline="") as f:
        f.write("id,lat,lon,type,detail,usable\n")
        for r in rows:
            lat, lon = (r.get("위도") or "").strip(), (r.get("경도") or "").strip()
            t = TYPE.get((r.get("시설유형코드") or "").strip())
            if not (NUM.match(lat) and NUM.match(lon) and t):
                dropped += 1
                continue
            la, lo = float(lat), float(lon)
            if not (37.40 < la < 37.72 and 126.75 < lo < 127.20):  # 서울 경계 상자 밖이면 버림
                dropped += 1
                continue
            detail = clean(r.get("상세위치"))
            if detail in JUNK or detail.endswith("안전센터"):
                detail = ""
            road = clean(r.get("소재지도로명주소")).replace("서울특별시 ", "")
            if not detail:
                detail = road
            elif road:
                detail = f"{road} · {detail}"
            usable = clean(r.get("사용가능여부"))
            f.write(f"{clean(r.get('시설번호'))},{la:.6f},{lo:.6f},{t},{detail},{usable}\n")
            kept += 1
    print(f"kept={kept} dropped={dropped}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
