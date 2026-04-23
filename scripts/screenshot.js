const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const SCREENSHOTS_DIR = path.join(__dirname, '../frontend/public/screenshots');
const BASE_URL = 'http://localhost:3000';
const TIMEOUT = 10000;

// 스크린샷 디렉토리 생성
if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  console.log(`📁 Created directory: ${SCREENSHOTS_DIR}`);
}

async function takeScreenshots() {
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();

    // 뷰포트 설정 (1280x900)
    await page.setViewportSize({ width: 1280, height: 900 });
    console.log('📱 Set viewport to 1280x900');

    // ============================================
    // 1️⃣ 검색 페이지 메인 (NQL 입력창 + 예시 쿼리)
    // ============================================
    console.log('📸 Taking search-main.png...');
    await page.goto(`${BASE_URL}`, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    await page.waitForTimeout(2000); // 추가 렌더링 대기
    try {
      await page.waitForSelector('textarea', { timeout: 5000 });
    } catch (e) {
      console.warn('⚠️  Textarea not found, proceeding anyway...');
    }
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, 'search-main.png'),
      fullPage: false
    });
    console.log('✅ search-main.png saved');

    // ============================================
    // 2️⃣ 검색 결과 (쿼리 실행 후)
    // ============================================
    console.log('📸 Taking search-result.png...');
    try {
      const textarea = await page.$('textarea');
      if (textarea) {
        await textarea.fill('keyword("AI chip")');
        const searchBtn = await page.$('button:has-text("검색")');
        if (searchBtn) {
          await searchBtn.click();
          await page.waitForTimeout(3000); // API 응답 대기
        }
      }
    } catch (e) {
      console.warn('⚠️  Could not fill search:', e.message);
    }

    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, 'search-result.png'),
      fullPage: false
    });
    console.log('✅ search-result.png saved');

    // ============================================
    // 3️⃣ 대시보드 페이지 (풀페이지)
    // ============================================
    console.log('📸 Taking dashboard-full.png...');
    await page.goto(`${BASE_URL}/dashboard`, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    await page.waitForTimeout(2000); // 데이터 로드 대기

    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, 'dashboard-full.png'),
      fullPage: true
    });
    console.log('✅ dashboard-full.png saved');

    // ============================================
    // 4️⃣ 대시보드 상단 (통계 카드)
    // ============================================
    console.log('📸 Taking dashboard-stats.png...');
    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, 'dashboard-stats.png'),
      fullPage: false,
      clip: { x: 0, y: 0, width: 1280, height: 500 }
    });
    console.log('✅ dashboard-stats.png saved');

    // ============================================
    // 5️⃣ 대시보드 히스토리 + 차트 (2컬럼 섹션)
    // ============================================
    console.log('📸 Taking dashboard-chart.png...');
    // 페이지를 아래로 스크롤하여 차트 섹션을 뷰포트에 가져오기
    await page.evaluate(() => {
      window.scrollBy(0, 600);
    });
    await page.waitForTimeout(500);

    await page.screenshot({
      path: path.join(SCREENSHOTS_DIR, 'dashboard-chart.png'),
      fullPage: false
    });
    console.log('✅ dashboard-chart.png saved');

    // ============================================
    // 완료
    // ============================================
    console.log('\n✨ All screenshots captured successfully!');
    console.log(`📁 Location: ${SCREENSHOTS_DIR}`);
    console.log('\n📸 Generated files:');
    fs.readdirSync(SCREENSHOTS_DIR).forEach(file => {
      const filePath = path.join(SCREENSHOTS_DIR, file);
      const stats = fs.statSync(filePath);
      console.log(`   - ${file} (${Math.round(stats.size / 1024)}KB)`);
    });

  } catch (error) {
    console.error('❌ Error during screenshot capture:', error.message);
    console.error('\n🔍 Troubleshooting:');
    console.error('   1. Ensure Spring Boot is running on port 8080');
    console.error('   2. Ensure Next.js dev server is running on port 3000');
    console.error('   3. Check that Elasticsearch is accessible');
    console.error('   4. Try: npm run dev (in frontend directory)');
    process.exit(1);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
}

// 실행
takeScreenshots();
